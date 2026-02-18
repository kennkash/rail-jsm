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
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeSet;

/**
 * Service for searching and retrieving Jira Issues
 * Provides JQL-based issue search functionality for portal display
 */
@Named
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAX_PAGE_SIZE = 100;

    /**
     * Facets can be expensive if result sets are huge; this caps the number of issues scanned
     * when computing distinct status/priority lists.
     *
     * If you want "always exact facets", set this very high, but beware performance.
     */
    private static final int MAX_FACET_SCAN_ISSUES = 5000;

    private static final Pattern PROJECT_KEY_PATTERN =
            Pattern.compile("project\\s*=\\s*\"?([A-Z0-9_\\-]+)\"?", Pattern.CASE_INSENSITIVE);

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
        return searchIssues(jqlQuery, startIndex, pageSize, null, null, null);
    }

    /**
     * Search issues using JQL query with optional server-side search/filter.
     * Also computes facets (distinct status/priority values across the entire result set)
     * so the UI can present complete filter options even when results are paged.
     */
    public IssueSearchResponseDTO searchIssues(
            String jqlQuery,
            int startIndex,
            int pageSize,
            String searchTerm,
            String statusFilter,
            String priorityFilter
    ) {
        long startTime = System.currentTimeMillis();
        log.debug("Searching issues with JQL: {}, startIndex: {}, pageSize: {}, search: {}, status: {}, priority: {}",
                jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);

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

            // This is the query used for the RESULT ROWS (includes status/priority filter)
            String enhancedJqlForResults = buildEnhancedJql(jqlQuery, searchTerm, statusFilter, priorityFilter);
            log.debug("Enhanced JQL (results): {}", enhancedJqlForResults);

            SearchService.ParseResult parseResult = searchService.parseQuery(currentUser, enhancedJqlForResults);
            if (!parseResult.isValid()) {
                log.warn("Invalid JQL query: {} - Errors: {}", enhancedJqlForResults, parseResult.getErrors());
                throw new IllegalArgumentException("Invalid JQL query: " + parseResult.getErrors());
            }

            Query queryForResults = parseResult.getQuery();
            PagerFilter pagerFilter = new PagerFilter(validatedStartIndex, validatedPageSize);

            resolvedJql = queryForResults.getQueryString();

            // ---- FACETS ----
            // Compute facets from "base query + searchTerm" ONLY (exclude status/priority filters),
            // so the filter picker includes values that exist anywhere in the full result set.
            Facets facets = null;
            try {
                String enhancedJqlForFacets = buildEnhancedJql(jqlQuery, searchTerm, null, null);
                SearchService.ParseResult facetsParse = searchService.parseQuery(currentUser, enhancedJqlForFacets);
                if (facetsParse.isValid()) {
                    facets = computeFacets(currentUser, facetsParse.getQuery(), MAX_FACET_SCAN_ISSUES);
                } else {
                    log.debug("Facet JQL invalid; skipping facets. JQL={}, errors={}",
                            enhancedJqlForFacets, facetsParse.getErrors());
                }
            } catch (Exception facetEx) {
                // Facets should never fail the whole request.
                log.warn("Facet computation failed; continuing without facets. jql={}", jqlQuery, facetEx);
            }
            // ----------------

            // Project key extraction / fallback
            projectKeyFromJql = extractProjectKeyFromJql(jqlQuery);
            if (projectKeyFromJql == null) {
                projectKeyFromJql = extractProjectKeyFromJql(resolvedJql);
            }
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
                    // Attach facets even to fallback when available
                    attachFacetsToResponseIfSupported(fallbackResponse, facets);
                    return fallbackResponse;
                }
            }

            // Execute paged search for rows
            SearchResults searchResults = searchService.search(currentUser, queryForResults, pagerFilter);
            Collection<Issue> issues = searchResults.getResults();

            List<IssueDTO> issueDTOs = issues.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            int total = searchResults.getTotal();

            // If zero results and service desk fallback is applicable, try it
            if (total == 0 && projectKeyFromJql != null && shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                log.debug("Zero results from JQL search; attempting service desk fallback for user {} project {}",
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
                    attachFacetsToResponseIfSupported(fallbackResponse, facets);
                    return fallbackResponse;
                }
            }

            if (total == 0) {
                log.info("Issue search returned no results for JQL '{}' (user: {}, resolvedJql: {}, startIndex: {}, pageSize: {})",
                        jqlQuery, currentUser.getKey(), resolvedJql, validatedStartIndex, validatedPageSize);
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

            // Attach facets to response (requires DTO support)
            attachFacetsToResponseIfSupported(response, facets);

            log.debug("Issue search completed: {} results out of {} total ({}ms) - searched as user: {} ({})",
                    issueDTOs.size(), total, response.getExecutionTimeMs(),
                    currentUser.getDisplayName(), currentUser.getKey());

            return response;

        } catch (SearchException e) {
            log.error("Error executing issue search with JQL: {}", jqlQuery, e);

            if (projectKeyFromJql != null && shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
                int validatedStartIndex = Math.max(startIndex, 0);

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

            throw new RuntimeException("Issue search failed: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during issue search", e);
            throw new RuntimeException("Issue search failed: " + e.getMessage(), e);
        }
    }

    public IssueSearchResponseDTO searchProjectIssues(String projectKey, int startIndex, int pageSize) {
        String jql = String.format("project = %s AND reporter = currentUser() ORDER BY created DESC", projectKey);
        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    public IssueSearchResponseDTO searchAllProjectIssues(String projectKey, int startIndex, int pageSize) {
        String jql = String.format("project = %s ORDER BY created DESC", projectKey);
        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    public IssueSearchResponseDTO searchProjectIssuesWithFilter(String projectKey, String additionalFilter, int startIndex, int pageSize) {
        String baseJql = String.format("project = %s", projectKey);
        String jql = additionalFilter != null && !additionalFilter.trim().isEmpty()
                ? baseJql + " AND " + additionalFilter + " ORDER BY created DESC"
                : baseJql + " ORDER BY created DESC";

        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

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
            log.debug("Could not resolve service desk for project {} as user {}: {}",
                    projectKey, currentUser.getKey(), e.getMessage());
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

            log.debug("Service desk fallback returned {} requests (hasNextPage: {}) for user {} in project {}",
                    issueDTOs.size(), response.hasNextPage(), currentUser.getKey(), projectKey);

            return dto;
        } catch (Exception ex) {
            log.warn("Service desk fallback failed for project {}: {}", projectKey, ex.getMessage());
            return null;
        }
    }

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
            log.debug("Unable to evaluate browse permission for project {}: {}",
                    project != null ? project.getKey() : "null", e.getMessage());
            return false;
        }
    }

    private boolean isServiceDeskProject(Project project) {
        try {
            return serviceDeskManager != null && serviceDeskManager.getServiceDeskForProject(project) != null;
        } catch (Exception e) {
            log.debug("Unable to determine if project {} is service desk: {}",
                    project != null ? project.getKey() : "null", e.getMessage());
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
     * Build enhanced JQL by appending search and filter clauses.
     */
    private String buildEnhancedJql(String baseJql, String searchTerm, String statusFilter, String priorityFilter) {
        if (baseJql == null || baseJql.trim().isEmpty()) {
            baseJql = "";
        }

        StringBuilder enhancedJql = new StringBuilder();

        String orderByClause = "";
        String jqlWithoutOrder = baseJql;
        int orderByIndex = baseJql.toUpperCase().lastIndexOf("ORDER BY");
        if (orderByIndex > 0) {
            orderByClause = baseJql.substring(orderByIndex);
            jqlWithoutOrder = baseJql.substring(0, orderByIndex).trim();
        }

        if (!jqlWithoutOrder.isEmpty()) {
            enhancedJql.append("(").append(jqlWithoutOrder).append(")");
        }

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

        if (!orderByClause.isEmpty()) {
            enhancedJql.append(" ").append(orderByClause);
        }

        String result = enhancedJql.toString().trim();
        return result.isEmpty() ? baseJql : result;
    }

    private String sanitizeJqlValue(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'");
    }

    private int estimateTotalCount(int startIndex, int resultSize, boolean hasNextPage, int pageSize) {
        int base = Math.max(startIndex, 0) + Math.max(resultSize, 0);
        if (hasNextPage) {
            return base + Math.max(pageSize, resultSize);
        }
        return base;
    }

    /**
     * ===== FACETS SUPPORT =====
     * Computes distinct status/priority values across the ENTIRE query result set
     * (bounded by MAX_FACET_SCAN_ISSUES to protect DC performance).
     */
    private static class Facets {
        private final List<String> statuses;
        private final List<String> priorities;
        private final boolean truncated;

        private Facets(List<String> statuses, List<String> priorities, boolean truncated) {
            this.statuses = statuses;
            this.priorities = priorities;
            this.truncated = truncated;
        }
    }

    private Facets computeFacets(ApplicationUser user, Query query, int maxScanIssues) throws SearchException {
        int pageSize = MAX_PAGE_SIZE;
        int start = 0;
        int scanned = 0;

        Set<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> priorities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        boolean truncated = false;

        while (true) {
            SearchResults page = searchService.search(user, query, new PagerFilter(start, pageSize));
            Collection<Issue> issues = page.getResults();
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

                scanned++;
                if (scanned >= maxScanIssues) {
                    truncated = true;
                    break;
                }
            }

            if (truncated) {
                break;
            }

            start += issues.size();
            if (start >= page.getTotal()) {
                break;
            }
        }

        return new Facets(new ArrayList<>(statuses), new ArrayList<>(priorities), truncated);
    }

    /**
     * Attaches facets onto IssueSearchResponseDTO **if** your DTO supports them.
     *
     * I will replace this with direct setters once you paste IssueSearchResponseDTO.java.
     */
    private void attachFacetsToResponseIfSupported(IssueSearchResponseDTO response, Facets facets) {
        if (response == null || facets == null) return;

        // We can't assume your DTO structure yet; once you paste IssueSearchResponseDTO.java,
        // I'll switch this to strongly-typed fields and remove reflection.
        try {
            // response.setFacetStatuses(...)
            java.lang.reflect.Method m1 = response.getClass().getMethod("setFacetStatuses", List.class);
            m1.invoke(response, facets.statuses);

            java.lang.reflect.Method m2 = response.getClass().getMethod("setFacetPriorities", List.class);
            m2.invoke(response, facets.priorities);

            java.lang.reflect.Method m3 = response.getClass().getMethod("setFacetTruncated", boolean.class);
            m3.invoke(response, facets.truncated);
        } catch (NoSuchMethodException e) {
            // DTO not updated yet; silently ignore
            log.debug("DTO does not yet support facets setters; skipping attaching facets.");
        } catch (Exception e) {
            log.warn("Failed attaching facets to response; continuing without facets.", e);
        }
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

        // NOTE: This section contains reflection elsewhere (extractObjectDisplayValue).
        // Keeping as-is for now since you already have it, but it's an Upgrade Risk for Jira 10.x.
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

            String typeKey = "";
            if (cf.getCustomFieldType() != null) {
                typeKey = cf.getCustomFieldType().getKey();
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
