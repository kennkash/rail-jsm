/* rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/service/IssueService.java  */
package com.samsungbuilder.jsm.service;

import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.bc.issue.search.SearchService;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.IssueManager;
import com.atlassian.jira.issue.priority.Priority;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.query.Query;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.ServiceDeskManager;
import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.servicedesk.api.request.CustomerRequest;
import com.atlassian.servicedesk.api.request.CustomerRequestQuery;
import com.atlassian.servicedesk.api.request.ServiceDeskCustomerRequestService;
import com.atlassian.servicedesk.api.util.paging.PagedResponse;
import com.atlassian.servicedesk.api.util.paging.SimplePagedRequest;
import com.samsungbuilder.jsm.dto.IssueDTO;
import com.samsungbuilder.jsm.dto.IssueSearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for searching and retrieving Jira Issues
 * Provides JQL-based issue search functionality for portal display
 */
@Named
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;
    private static final Pattern PROJECT_KEY_PATTERN = Pattern.compile("project\\s*=\\s*\"?([A-Z0-9_\\-]+)\"?", Pattern.CASE_INSENSITIVE);

    private final SearchService searchService;
    private final IssueManager issueManager;
    private final ProjectManager projectManager;
    private final JiraAuthenticationContext authenticationContext;
    private final AvatarService avatarService;
    private final PermissionManager permissionManager;
    private final ServiceDeskCustomerRequestService customerRequestService;
    private final ServiceDeskManager serviceDeskManager;

    @Inject
    public IssueService(
            @ComponentImport SearchService searchService,
            @ComponentImport IssueManager issueManager,
            @ComponentImport ProjectManager projectManager,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            @ComponentImport AvatarService avatarService,
            @ComponentImport PermissionManager permissionManager,
            @ComponentImport ServiceDeskCustomerRequestService customerRequestService,
            @ComponentImport ServiceDeskManager serviceDeskManager
    ) {
        this.searchService = searchService;
        this.issueManager = issueManager;
        this.projectManager = projectManager;
        this.authenticationContext = authenticationContext;
        this.avatarService = avatarService;
        this.permissionManager = permissionManager;
        this.customerRequestService = customerRequestService;
        this.serviceDeskManager = serviceDeskManager;
    }

    /**
     * Search issues using JQL query (backward compatible - no server-side search/filter)
     */
    public IssueSearchResponseDTO searchIssues(String jqlQuery, int startIndex, int pageSize) {
        return searchIssues(jqlQuery, startIndex, pageSize, null, null, null, null, null, false);
    }

    /**
     * Search issues using JQL query with optional server-side search/filter.
     * Backward compatible signature (no sort/facets).
     */
    public IssueSearchResponseDTO searchIssues(String jqlQuery, int startIndex, int pageSize,
                                              String searchTerm, String statusFilter, String priorityFilter) {
        return searchIssues(jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, null, null, false);
    }

    /**
     * Search issues using JQL query with:
     * - optional server-side search/filter (across ENTIRE result set)
     * - optional server-side sorting (ORDER BY <field> ASC|DESC)
     * - optional facets for UI filters (distinct statuses/priorities across ENTIRE result set)
     */
    public IssueSearchResponseDTO searchIssues(
            String jqlQuery,
            int startIndex,
            int pageSize,
            String searchTerm,
            String statusFilter,
            String priorityFilter,
            String sortField,
            String sortDir,
            boolean includeFacets
    ) {
        long startTime = System.currentTimeMillis();
        log.debug("Searching issues with JQL: {}, startIndex: {}, pageSize: {}, search: {}, status: {}, priority: {}, sortField: {}, sortDir: {}, facets: {}",
                jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, sortField, sortDir, includeFacets);

        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        if (currentUser == null) {
            log.warn("No authenticated user found for issue search");
            IssueSearchResponseDTO emptyResponse = createEmptyResponse(jqlQuery, startIndex, pageSize);
            emptyResponse.setSearchedAsUserKey(null);
            emptyResponse.setSearchedAsUserName(null);
            emptyResponse.setSearchedAsUserDisplayName("Anonymous (not authenticated)");
            return emptyResponse;
        }

        String projectKeyFromJql = null;
        String resolvedJql = null;

        try {
            int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
            int validatedStartIndex = Math.max(startIndex, 0);

            // Build enhanced JQL: base + search/filter + ORDER BY override (if provided)
            String enhancedJql = buildEnhancedJql(jqlQuery, searchTerm, statusFilter, priorityFilter, sortField, sortDir);
            log.debug("Enhanced JQL: {}", enhancedJql);

            SearchService.ParseResult parseResult = searchService.parseQuery(currentUser, enhancedJql);
            if (!parseResult.isValid()) {
                log.warn("Invalid JQL query: {} - Errors: {}", enhancedJql, parseResult.getErrors());
                throw new IllegalArgumentException("Invalid JQL query: " + parseResult.getErrors());
            }

            Query query = parseResult.getQuery();
            PagerFilter pagerFilter = new PagerFilter(validatedStartIndex, validatedPageSize);

            // Resolved JQL (with currentUser() resolved)
            resolvedJql = query.getQueryString();

            // Determine project key for fallback logic
            projectKeyFromJql = extractProjectKeyFromJql(jqlQuery);
            if (projectKeyFromJql == null) {
                projectKeyFromJql = extractProjectKeyFromJql(resolvedJql);
            }

            // If user cannot browse project, fall back to service desk customer requests
            if (shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                log.debug("Using Service Desk customer request fallback for user {} in project {}",
                        currentUser.getKey(), projectKeyFromJql);
                IssueSearchResponseDTO fallbackResponse = searchCustomerRequestsForProject(
                        projectKeyFromJql,
                        validatedStartIndex,
                        validatedPageSize,
                        currentUser,
                        jqlQuery,
                        resolvedJql,
                        startTime
                );
                if (fallbackResponse != null) {
                    // NOTE: facets/sort are not supported in the service desk fallback path here.
                    // If you need facets there too, we can add a separate paging aggregator using customerRequestService.
                    return fallbackResponse;
                }
            }

            // Execute search (paged)
            SearchResults searchResults = searchService.search(currentUser, query, pagerFilter);
            Collection<Issue> issues = searchResults.getResults();

            List<IssueDTO> issueDTOs = issues.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            int total = searchResults.getTotal();

            // If no results and fallback should apply, try it
            if (total == 0 && projectKeyFromJql != null && shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                IssueSearchResponseDTO fallbackResponse = searchCustomerRequestsForProject(
                        projectKeyFromJql,
                        validatedStartIndex,
                        validatedPageSize,
                        currentUser,
                        jqlQuery,
                        resolvedJql,
                        startTime
                );
                if (fallbackResponse != null) {
                    return fallbackResponse;
                }
            }

            IssueSearchResponseDTO response = new IssueSearchResponseDTO(
                    issueDTOs,
                    total,
                    validatedStartIndex,
                    validatedPageSize
            );
            response.setJqlQuery(jqlQuery);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            response.setSearchedAsUserKey(currentUser.getKey());
            response.setSearchedAsUserName(currentUser.getUsername());
            response.setSearchedAsUserDisplayName(currentUser.getDisplayName());
            response.setResolvedJqlQuery(resolvedJql);

            // NEW: facets (distinct statuses/priorities across ENTIRE result set)
            if (includeFacets) {
                response.setFacets(computeFacetsPaged(currentUser, query));
            }

            return response;

        } catch (SearchException e) {
            log.error("Error executing issue search with JQL: {}", jqlQuery, e);
            throw new RuntimeException("Issue search failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during issue search", e);
            throw new RuntimeException("Issue search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Search issues for a specific project with current user filter
     */
    public IssueSearchResponseDTO searchProjectIssues(String projectKey, int startIndex, int pageSize) {
        String jql = String.format("project = %s AND reporter = currentUser() ORDER BY created DESC", projectKey);
        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    /**
     * Search issues for a specific project (all issues user can see)
     */
    public IssueSearchResponseDTO searchAllProjectIssues(String projectKey, int startIndex, int pageSize) {
        String jql = String.format("project = %s ORDER BY created DESC", projectKey);
        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    /**
     * Get issues by project with custom JQL filter
     */
    public IssueSearchResponseDTO searchProjectIssuesWithFilter(String projectKey, String additionalFilter, int startIndex, int pageSize) {
        String baseJql = String.format("project = %s", projectKey);
        String jql = additionalFilter != null && !additionalFilter.trim().isEmpty()
                ? baseJql + " AND " + additionalFilter + " ORDER BY created DESC"
                : baseJql + " ORDER BY created DESC";

        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    /**
     * Fallback search for service desk customers without Browse permission.
     * Uses the Service Desk customer request API to return only requests the user can see.
     */
    private IssueSearchResponseDTO searchCustomerRequestsForProject(
            String projectKey,
            int startIndex,
            int pageSize,
            ApplicationUser currentUser,
            String originalJql,
            String resolvedJql,
            long startTime
    ) {
        if (customerRequestService == null || serviceDeskManager == null || projectKey == null) {
            return null;
        }

        Project project = projectManager.getProjectObjByKey(projectKey);
        if (project == null) {
            return null;
        }

        ServiceDesk serviceDesk = null;
        try {
            serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
        } catch (Exception e) {
            log.debug("Could not resolve service desk for project {} as user {}: {}", projectKey, currentUser.getKey(), e.getMessage());
        }

        int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
        int validatedStartIndex = Math.max(startIndex, 0);

        try {
            CustomerRequestQuery.Builder builder = customerRequestService.newQueryBuilder()
                    .requestOwnership(CustomerRequestQuery.REQUEST_OWNERSHIP.ALL_ORGANIZATIONS_AND_GROUPS)
                    .pagedRequest(SimplePagedRequest.paged(validatedStartIndex, validatedPageSize));

            if (serviceDesk != null) {
                builder = builder.serviceDesk(serviceDesk.getId());
            }

            CustomerRequestQuery query = builder.build();
            PagedResponse<CustomerRequest> response = customerRequestService.getCustomerRequests(currentUser, query);

            List<IssueDTO> issueDTOs = response.getResults().stream()
                    .map(CustomerRequest::getIssue)
                    .filter(Objects::nonNull)
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            int totalCount = estimateTotalCount(validatedStartIndex, issueDTOs.size(), response.hasNextPage(), validatedPageSize);

            IssueSearchResponseDTO dto = new IssueSearchResponseDTO(
                    issueDTOs,
                    totalCount,
                    validatedStartIndex,
                    validatedPageSize
            );
            dto.setHasNextPage(response.hasNextPage());
            dto.setHasPreviousPage(validatedStartIndex > 0);
            dto.setProjectKey(projectKey);
            dto.setJqlQuery(originalJql);
            dto.setResolvedJqlQuery(resolvedJql != null ? resolvedJql : originalJql);
            dto.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            dto.setSearchedAsUserKey(currentUser.getKey());
            dto.setSearchedAsUserName(currentUser.getUsername());
            dto.setSearchedAsUserDisplayName(currentUser.getDisplayName());

            return dto;
        } catch (Exception ex) {
            log.warn("Service desk fallback failed for project {}: {}", projectKey, ex.getMessage());
            return null;
        }
    }

    /**
     * Determine whether the caller lacks Browse permission but can be served via service desk APIs.
     */
    private boolean shouldUseCustomerRequestFallback(ApplicationUser user, String projectKey) {
        if (user == null || projectKey == null || permissionManager == null) {
            return false;
        }

        Project project = projectManager.getProjectObjByKey(projectKey);
        if (project == null) {
            return false;
        }

        if (hasBrowsePermission(user, project)) {
            return false;
        }

        return isServiceDeskProject(project) && customerRequestService != null && serviceDeskManager != null;
    }

    private boolean hasBrowsePermission(ApplicationUser user, Project project) {
        try {
            return permissionManager != null && permissionManager.hasPermission(ProjectPermissions.BROWSE_PROJECTS, project, user);
        } catch (Exception e) {
            log.debug("Unable to evaluate browse permission for project {}: {}", project != null ? project.getKey() : "null", e.getMessage());
            return false;
        }
    }

    private boolean isServiceDeskProject(Project project) {
        try {
            return serviceDeskManager != null && serviceDeskManager.getServiceDeskForProject(project) != null;
        } catch (Exception e) {
            log.debug("Unable to determine if project {} is service desk: {}", project != null ? project.getKey() : "null", e.getMessage());
            return false;
        }
    }

    private String extractProjectKeyFromJql(String jqlQuery) {
        if (jqlQuery == null) {
            return null;
        }
        Matcher matcher = PROJECT_KEY_PATTERN.matcher(jqlQuery);
        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }
        return null;
    }

    /**
     * Build enhanced JQL by appending search and filter clauses,
     * and optionally overriding ORDER BY for server-side sorting.
     */
    private String buildEnhancedJql(
            String baseJql,
            String searchTerm,
            String statusFilter,
            String priorityFilter,
            String sortField,
            String sortDir
    ) {
        if (baseJql == null || baseJql.trim().isEmpty()) {
            baseJql = "";
        }

        StringBuilder enhancedJql = new StringBuilder();

        // Extract ORDER BY clause if present
        String baseOrderByClause = "";
        String jqlWithoutOrder = baseJql;
        int orderByIndex = baseJql.toUpperCase().lastIndexOf("ORDER BY");
        if (orderByIndex > 0) {
            baseOrderByClause = baseJql.substring(orderByIndex).trim();
            jqlWithoutOrder = baseJql.substring(0, orderByIndex).trim();
        }

        if (!jqlWithoutOrder.isEmpty()) {
            enhancedJql.append("(").append(jqlWithoutOrder).append(")");
        }

        // Text search clause
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String trimmedSearch = searchTerm.trim();
            String sanitizedSearch = sanitizeJqlValue(trimmedSearch);
            if (enhancedJql.length() > 0) {
                enhancedJql.append(" AND ");
            }

            boolean looksLikeIssueKey = trimmedSearch.matches("^[A-Za-z]+-\\d+$");

            if (looksLikeIssueKey) {
                enhancedJql.append("(key = \"").append(sanitizedSearch.toUpperCase()).append("\"")
                        .append(" OR summary ~ \"").append(sanitizedSearch).append("\"")
                        .append(" OR description ~ \"").append(sanitizedSearch).append("\")");
            } else {
                enhancedJql.append("(text ~ \"").append(sanitizedSearch).append("\"")
                        .append(" OR key ~ \"").append(sanitizedSearch).append("\")");
            }
        }

        // Status filter clause
        if (statusFilter != null && !statusFilter.trim().isEmpty()) {
            String[] statuses = statusFilter.split(",");
            if (statuses.length > 0) {
                if (enhancedJql.length() > 0) {
                    enhancedJql.append(" AND ");
                }
                enhancedJql.append("status IN (");
                for (int i = 0; i < statuses.length; i++) {
                    if (i > 0) enhancedJql.append(", ");
                    enhancedJql.append("\"").append(sanitizeJqlValue(statuses[i].trim())).append("\"");
                }
                enhancedJql.append(")");
            }
        }

        // Priority filter clause
        if (priorityFilter != null && !priorityFilter.trim().isEmpty()) {
            String[] priorities = priorityFilter.split(",");
            if (priorities.length > 0) {
                if (enhancedJql.length() > 0) {
                    enhancedJql.append(" AND ");
                }
                enhancedJql.append("priority IN (");
                for (int i = 0; i < priorities.length; i++) {
                    if (i > 0) enhancedJql.append(", ");
                    enhancedJql.append("\"").append(sanitizeJqlValue(priorities[i].trim())).append("\"");
                }
                enhancedJql.append(")");
            }
        }

        // ORDER BY: prefer UI override if provided, else keep base ORDER BY
        String uiOrderByClause = buildOrderByClause(sortField, sortDir);
        if (!uiOrderByClause.isEmpty()) {
            enhancedJql.append(" ").append(uiOrderByClause);
        } else if (!baseOrderByClause.isEmpty()) {
            enhancedJql.append(" ").append(baseOrderByClause);
        }

        String result = enhancedJql.toString().trim();
        return result.isEmpty() ? baseJql : result;
    }

    /**
     * Whitelist supported ORDER BY fields to prevent JQL injection.
     * Allows common system fields + customfield_XXXXX.
     */
    private String buildOrderByClause(String sortField, String sortDir) {
        if (sortField == null || sortField.trim().isEmpty()) {
            return "";
        }

        String dir = "ASC";
        if ("desc".equalsIgnoreCase(sortDir)) {
            dir = "DESC";
        }

        String field;
        switch (sortField) {
            case "key": field = "key"; break;
            case "summary": field = "summary"; break;
            case "status": field = "status"; break;
            case "priority": field = "priority"; break;
            case "issueType": field = "issuetype"; break;
            case "created": field = "created"; break;
            case "updated": field = "updated"; break;
            case "dueDate": field = "duedate"; break;
            case "reporter": field = "reporter"; break;
            case "assignee": field = "assignee"; break;
            case "resolution": field = "resolution"; break;
            default:
                // Optional: allow sorting by customfield_12345
                if (sortField.startsWith("customfield_") && sortField.matches("^customfield_\\d+$")) {
                    field = sortField;
                } else {
                    return "";
                }
        }

        return "ORDER BY " + field + " " + dir;
    }

    /**
     * Sanitize a value for safe use in JQL queries.
     */
    private String sanitizeJqlValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'");
    }

    /**
     * Compute facets across ENTIRE result set by paging internally and aggregating distinct values.
     * NOTE: This does NOT return all issues; it only collects distinct status/priority values.
     */
    private Map<String, List<String>> computeFacetsPaged(ApplicationUser user, Query query) {
        final int facetPageSize = 500; // tune as needed

        Set<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> priorities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        int startAt = 0;
        while (true) {
            PagerFilter pf = new PagerFilter(startAt, facetPageSize);
            SearchResults sr;
            try {
                sr = searchService.search(user, query, pf);
            } catch (SearchException e) {
                log.warn("Facet paging failed: {}", e.getMessage());
                break;
            }

            Collection<Issue> issues = sr.getResults();
            if (issues == null || issues.isEmpty()) {
                break;
            }

            for (Issue i : issues) {
                if (i.getStatus() != null && i.getStatus().getName() != null) {
                    statuses.add(i.getStatus().getName());
                }
                if (i.getPriority() != null && i.getPriority().getName() != null) {
                    priorities.add(i.getPriority().getName());
                }
            }

            startAt += issues.size();
            if (startAt >= sr.getTotal()) {
                break;
            }
        }

        Map<String, List<String>> facets = new HashMap<>();
        facets.put("statuses", new ArrayList<>(statuses));
        facets.put("priorities", new ArrayList<>(priorities));
        return facets;
    }

    private int estimateTotalCount(int startIndex, int resultSize, boolean hasNextPage, int pageSize) {
        int base = Math.max(startIndex, 0) + Math.max(resultSize, 0);
        if (hasNextPage) {
            return base + Math.max(pageSize, resultSize);
        }
        return base;
    }

    /**
     * Convert Jira Issue to DTO
     */
    private IssueDTO convertToDTO(Issue issue) {
        IssueDTO dto = new IssueDTO();

        dto.setId(issue.getId().toString());
        dto.setKey(issue.getKey());
        dto.setSummary(issue.getSummary());
        dto.setDescription(issue.getDescription());

        Project project = issue.getProjectObject();
        if (project != null) {
            dto.setProjectKey(project.getKey());
            dto.setProjectName(project.getName());

            String serviceDeskId = null;
            try {
                com.atlassian.servicedesk.api.ServiceDeskManager sdManager =
                        ComponentAccessor.getOSGiComponentInstanceOfType(
                                com.atlassian.servicedesk.api.ServiceDeskManager.class
                        );
                if (sdManager != null) {
                    com.atlassian.servicedesk.api.ServiceDesk serviceDesk =
                            sdManager.getServiceDeskForProject(project);
                    if (serviceDesk != null) {
                        serviceDeskId = String.valueOf(serviceDesk.getId());
                        dto.setServiceDeskId(serviceDeskId);
                    }
                }
            } catch (Exception e) {
                log.warn("Could not resolve Service Desk ID for issue {}: {}", issue.getKey(), e.getMessage());
            }
        }

        if (issue.getStatus() != null) {
            dto.setStatus(issue.getStatus().getName());
            dto.setStatusId(issue.getStatus().getId());
            dto.setStatusIconUrl(issue.getStatus().getIconUrl());
            if (issue.getStatus().getStatusCategory() != null) {
                dto.setStatusCategoryKey(issue.getStatus().getStatusCategory().getKey());
            }
        }

        Priority priority = issue.getPriority();
        if (priority != null) {
            dto.setPriority(priority.getName());
            dto.setPriorityId(priority.getId());
            dto.setPriorityIconUrl(priority.getIconUrl());
        }

        if (issue.getIssueType() != null) {
            dto.setIssueType(issue.getIssueType().getName());
            dto.setIssueTypeId(issue.getIssueType().getId());
            dto.setIssueTypeIconUrl(issue.getIssueType().getIconUrl());
        }

        dto.setCreated(issue.getCreated());
        dto.setUpdated(issue.getUpdated());
        dto.setDueDate(issue.getDueDate());

        ApplicationUser reporter = issue.getReporter();
        if (reporter != null) {
            dto.setReporter(reporter.getKey());
            dto.setReporterDisplayName(reporter.getDisplayName());
            dto.setReporterEmailAddress(reporter.getEmailAddress());
            dto.setReporterAvatarUrl(getAvatarUrl(reporter));
        }

        ApplicationUser assignee = issue.getAssignee();
        if (assignee != null) {
            dto.setAssignee(assignee.getKey());
            dto.setAssigneeDisplayName(assignee.getDisplayName());
            dto.setAssigneeEmailAddress(assignee.getEmailAddress());
            dto.setAssigneeAvatarUrl(getAvatarUrl(assignee));
        }

        if (issue.getResolution() != null) {
            dto.setResolution(issue.getResolution().getName());
            dto.setResolutionId(issue.getResolution().getId());
            dto.setResolutionDate(issue.getResolutionDate());
        }

        if (issue.getLabels() != null && !issue.getLabels().isEmpty()) {
            dto.setLabels(issue.getLabels().stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
        }

        if (issue.getComponents() != null) {
            dto.setComponents(issue.getComponents().stream()
                    .map(component -> component.getName())
                    .toArray(String[]::new));
        }

        if (issue.getFixVersions() != null) {
            dto.setFixVersions(issue.getFixVersions().stream()
                    .map(version -> version.getName())
                    .toArray(String[]::new));
        }

        if (issue.getAffectedVersions() != null) {
            dto.setAffectedVersions(issue.getAffectedVersions().stream()
                    .map(version -> version.getName())
                    .toArray(String[]::new));
        }

        dto.setTimeOriginalEstimate(issue.getOriginalEstimate());
        dto.setTimeEstimate(issue.getEstimate());
        dto.setTimeSpent(issue.getTimeSpent());

        dto.setEnvironment(issue.getEnvironment());
        dto.setVotes(issue.getVotes() != null ? issue.getVotes().intValue() : null);
        dto.setWatcherCount(issue.getWatches() != null ? issue.getWatches().intValue() : null);

        try {
            var customFieldManager = com.atlassian.jira.component.ComponentAccessor.getCustomFieldManager();
            if (customFieldManager != null) {
                var customFields = customFieldManager.getCustomFieldObjects(issue);
                for (var cf : customFields) {
                    Object value = issue.getCustomFieldValue(cf);
                    if (value != null) {
                        String displayValue = extractCustomFieldDisplayValue(cf, value);
                        if (displayValue != null) {
                            dto.setCustomField(cf.getId(), displayValue);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Error extracting custom fields for issue {}: {}", issue.getKey(), e.getMessage());
        }

        return dto;
    }

    private String extractCustomFieldDisplayValue(com.atlassian.jira.issue.fields.CustomField cf, Object value) {
        try {
            if (value == null) {
                return null;
            }

            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Number) {
                return value.toString();
            } else if (value instanceof java.util.Date) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) value);
            } else if (value instanceof com.atlassian.jira.issue.customfields.option.Option) {
                return ((com.atlassian.jira.issue.customfields.option.Option) value).getValue();
            } else if (value instanceof ApplicationUser) {
                return ((ApplicationUser) value).getDisplayName();
            } else if (value instanceof java.util.Map) {
                java.util.Map<?, ?> map = (java.util.Map<?, ?>) value;
                StringBuilder sb = new StringBuilder();
                Object parent = map.get("");
                Object child = map.get("1");
                if (parent instanceof com.atlassian.jira.issue.customfields.option.Option) {
                    sb.append(((com.atlassian.jira.issue.customfields.option.Option) parent).getValue());
                }
                if (child instanceof com.atlassian.jira.issue.customfields.option.Option) {
                    if (sb.length() > 0) sb.append(" - ");
                    sb.append(((com.atlassian.jira.issue.customfields.option.Option) child).getValue());
                }
                return sb.length() > 0 ? sb.toString() : null;
            } else if (value instanceof java.util.Collection) {
                java.util.Collection<?> collection = (java.util.Collection<?>) value;
                if (collection.isEmpty()) {
                    return null;
                }
                StringBuilder sb = new StringBuilder();
                for (Object item : collection) {
                    if (sb.length() > 0) sb.append(", ");
                    String itemValue = extractCollectionItemValue(item);
                    if (itemValue != null && !itemValue.isEmpty()) {
                        sb.append(itemValue);
                    }
                }
                return sb.length() > 0 ? sb.toString() : null;
            } else {
                return extractObjectDisplayValue(value);
            }
        } catch (Exception e) {
            log.debug("Error extracting display value for custom field {} (type: {}): {}",
                    cf.getId(), cf.getCustomFieldType() != null ? cf.getCustomFieldType().getKey() : "unknown", e.getMessage());
            return null;
        }
    }

    private String extractCollectionItemValue(Object item) {
        if (item == null) {
            return null;
        }

        if (item instanceof String) {
            return (String) item;
        } else if (item instanceof com.atlassian.jira.issue.customfields.option.Option) {
            return ((com.atlassian.jira.issue.customfields.option.Option) item).getValue();
        } else if (item instanceof ApplicationUser) {
            return ((ApplicationUser) item).getDisplayName();
        } else if (item instanceof com.atlassian.jira.issue.label.Label) {
            return ((com.atlassian.jira.issue.label.Label) item).getLabel();
        } else {
            return extractObjectDisplayValue(item);
        }
    }

    private String extractObjectDisplayValue(Object obj) {
        if (obj == null) {
            return null;
        }

        String[] methodNames = {"getName", "getDisplayName", "getLabel", "getValue", "getKey", "getObjectLabel", "getObjectKey"};

        for (String methodName : methodNames) {
            try {
                java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
                Object result = method.invoke(obj);
                if (result != null) {
                    String stringResult = result.toString();
                    if (!stringResult.isEmpty() && !stringResult.contains("@")) {
                        return stringResult;
                    }
                }
            } catch (NoSuchMethodException e) {
                // ignore
            } catch (Exception e) {
                log.trace("Error invoking {} on {}: {}", methodName, obj.getClass().getSimpleName(), e.getMessage());
            }
        }

        String str = obj.toString();
        if (!str.contains("@") && str.length() < 500) {
            return str;
        }

        log.debug("Could not extract display value from object of type: {}", obj.getClass().getName());
        return null;
    }

    private String getAvatarUrl(ApplicationUser user) {
        try {
            if (avatarService != null && user != null) {
                URI avatarUri = avatarService.getAvatarURL(user, user, Avatar.Size.SMALL);
                return avatarUri != null ? avatarUri.toString() : null;
            }
        } catch (Exception e) {
            log.debug("Could not get avatar URL for user {}: {}", user != null ? user.getKey() : "null", e.getMessage());
        }
        return null;
    }

    private IssueSearchResponseDTO createEmptyResponse(String jqlQuery, int startIndex, int pageSize) {
        IssueSearchResponseDTO response = new IssueSearchResponseDTO(
                Collections.emptyList(), 0, startIndex, pageSize
        );
        response.setJqlQuery(jqlQuery);
        response.setExecutionTimeMs(0);
        return response;
    }

    public boolean canUserSeeProject(String projectKey) {
        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return false;
            }

            Project project = projectManager.getProjectObjByKey(projectKey);
            if (project == null) {
                return false;
            }

            String testJql = String.format("project = %s", projectKey);
            SearchService.ParseResult parseResult = searchService.parseQuery(currentUser, testJql);
            return parseResult.isValid();

        } catch (Exception e) {
            log.debug("Error checking project permissions for {}: {}", projectKey, e.getMessage());
            return false;
        }
    }
}
