// rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/service/IssueService.java
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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
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

    /**
    * Safety cap for facet scanning (status + priority). We only need unique values,
    * but scanning an unbounded result set can be expensive.
    */
    private static final int MAX_FACET_SCAN_ISSUES = 2000;
    private static final int FACET_PAGE_SIZE = 500;

    /**
    * Sort field allow-list to prevent JQL injection.
    * NOTE: Custom fields are handled separately via customfield_12345 => cf[12345].
    */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
        "key",
        "summary",
        "status",
        "priority",
        "created",
        "updated",
        "due",
        "assignee",
        "reporter",
        "issuetype",
        "resolution"
    );

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
     *
     * @param jqlQuery The JQL query string
     * @param startIndex The start index for pagination (0-based)
     * @param pageSize The number of results per page
     * @return Search response with paginated results
     */
    public IssueSearchResponseDTO searchIssues(String jqlQuery, int startIndex, int pageSize) {
        return searchIssues(jqlQuery, startIndex, pageSize, null, null, null, null, "asc", false);
    }

    /**
     * Search issues using JQL query with optional server-side search/filter.
     * This enables search and filtering across the ENTIRE result set, not just the current page.
     *
     * @param jqlQuery The JQL query string
     * @param startIndex The start index for pagination (0-based)
     * @param pageSize The number of results per page
     * @param searchTerm Optional text search term (searches key, summary, and description)
     * @param statusFilter Optional status filter (comma-separated status names)
     * @param priorityFilter Optional priority filter (comma-separated priority names)
     * @return Search response with paginated results
     */
    public IssueSearchResponseDTO searchIssues(String jqlQuery, int startIndex, int pageSize,
                                                String searchTerm, String statusFilter, String priorityFilter) {
        return searchIssues(jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, null, "asc", false);
    }

    /**
    * Search issues using JQL query with optional server-side search/filter, sorting, and facets.
    *
    * Sorting here applies to the ENTIRE result set (server-side ORDER BY), not just the current page.
    * Facets (status + priority) are computed across the ENTIRE result set (bounded by MAX_FACET_SCAN_ISSUES).
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
        log.debug(
            "Searching issues with JQL: {}, startIndex: {}, pageSize: {}, search: {}, status: {}, priority: {}, sortField: {}, sortDir: {}, facets: {}",
            jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, sortField, sortDir, includeFacets
        );

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
            // Validate and sanitize pagination parameters
            int validatedPageSize = Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
            int validatedStartIndex = Math.max(startIndex, 0);

            // Build enhanced JQL with search/filter terms (server-side filtering)
            String enhancedJql = buildEnhancedJql(jqlQuery, searchTerm, statusFilter, priorityFilter);
            // Apply server-side ORDER BY (sort applies across entire result set)
            String normalizedSortField = normalizeSortField(sortField);
            String normalizedSortDir = normalizeSortDir(sortDir);
            if (normalizedSortField != null) {
                enhancedJql = applyOrderBy(enhancedJql, normalizedSortField, normalizedSortDir);
            }
            log.debug("Enhanced JQL: {}", enhancedJql);

            // Parse and validate the enhanced JQL query
            SearchService.ParseResult parseResult = searchService.parseQuery(currentUser, enhancedJql);
            if (!parseResult.isValid()) {
                log.warn("Invalid JQL query: {} - Errors: {}", enhancedJql, parseResult.getErrors());
                throw new IllegalArgumentException("Invalid JQL query: " + parseResult.getErrors());
            }

            Query query = parseResult.getQuery();
            PagerFilter pagerFilter = new PagerFilter(validatedStartIndex, validatedPageSize);

            // Get the resolved JQL query string (with currentUser() replaced)
            resolvedJql = query.getQueryString();

            // If user cannot browse the project, fall back to service desk customer requests
            projectKeyFromJql = extractProjectKeyFromJql(jqlQuery);
            if (projectKeyFromJql == null) {
                projectKeyFromJql = extractProjectKeyFromJql(resolvedJql);
            }
            if (shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                log.debug("Using Service Desk customer request fallback for user {} in project {}", currentUser.getKey(), projectKeyFromJql);
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

            // Execute search
            SearchResults searchResults = searchService.search(currentUser, query, pagerFilter);
            Collection<Issue> issues = searchResults.getResults();

            // Convert to DTOs
            List<IssueDTO> issueDTOs = issues.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            int total = searchResults.getTotal();

            // If we got no results but this is a service desk project and the user lacks Browse, try the customer fallback
            if (total == 0 && projectKeyFromJql != null && shouldUseCustomerRequestFallback(currentUser, projectKeyFromJql)) {
                log.debug("Zero results from JQL search; attempting service desk fallback for user {} project {}", currentUser.getKey(), projectKeyFromJql);
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

            if (total == 0) {
                log.info("Issue search returned no results for JQL '{}' (user: {}, resolvedJql: {}, startIndex: {}, pageSize: {})",
                        jqlQuery, currentUser.getKey(), resolvedJql, validatedStartIndex, validatedPageSize);
            }

            // Build response with user context for debugging
            IssueSearchResponseDTO response = new IssueSearchResponseDTO(
                    issueDTOs,
                    total,
                    validatedStartIndex,
                    validatedPageSize
            );
            response.setJqlQuery(jqlQuery);
            response.setExecutionTimeMs(System.currentTimeMillis() - startTime);

            // Add user context for permission debugging
            response.setSearchedAsUserKey(currentUser.getKey());
            response.setSearchedAsUserName(currentUser.getUsername());
            response.setSearchedAsUserDisplayName(currentUser.getDisplayName());
            response.setResolvedJqlQuery(resolvedJql);

            // Facets (status + priority) for filter dropdowns across ENTIRE result set.
            if (includeFacets) {
                try {
                    // IMPORTANT: facets should reflect the full result set for the base query + searchTerm,
                    // but NOT be constrained by the user's current status/priority selections.
                    String facetsJql = buildEnhancedJql(jqlQuery, searchTerm, null, null);
                    Map<String, List<String>> facets = computeFacets(currentUser, facetsJql);
                    response.setFacets(facets);
                } catch (Exception facetEx) {
                    // Never fail the request due to facet computation.
                    log.warn("Facet computation failed; continuing without facets. jql='{}' user='{}' msg='{}'",
                             jqlQuery, currentUser.getKey(), facetEx.getMessage());
                }
            }

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

    private String normalizeSortDir(String sortDir) {
        if (sortDir == null) {
            return "asc";
        }
        String dir = sortDir.trim().toLowerCase();
        return "desc".equals(dir) ? "desc" : "asc";
    }

    /**
    * Normalize sort field into a safe JQL field reference.
    *
    * Supported:
    *  - system fields in ALLOWED_SORT_FIELDS
    *  - customfield_12345 => cf[12345]
    */
    private String normalizeSortField(String sortField) {
        if (sortField == null) {
            return null;
        }
        String raw = sortField.trim();
        if (raw.isEmpty()) {
            return null;
        }

        // customfield_12345 => cf[12345]
        Matcher m = Pattern.compile("^customfield_(\\d+)$", Pattern.CASE_INSENSITIVE).matcher(raw);
        if (m.matches()) {
            return "cf[" + m.group(1) + "]";
        }

        String normalized = raw.toLowerCase();
        if (ALLOWED_SORT_FIELDS.contains(normalized)) {
            return normalized;
        }

        // Reject anything else (avoid JQL injection)
        log.debug("Ignoring unsupported sortField '{}' (not allow-listed)", sortField);
        return null;
    }

    
    /**
    * Strip any existing ORDER BY clause and apply a new ORDER BY.
    */
    private String applyOrderBy(String baseJql, String sortField, String sortDir) {
        if (baseJql == null) {
            baseJql = "";
        }

        String trimmed = baseJql.trim();
        int orderByIndex = trimmed.toUpperCase().lastIndexOf("ORDER BY");
        String withoutOrder = orderByIndex > 0 ? trimmed.substring(0, orderByIndex).trim() : trimmed;
        
        if (withoutOrder.isEmpty()) {
            return "ORDER BY " + sortField + " " + sortDir;
        }
        return withoutOrder + " ORDER BY " + sortField + " " + sortDir;
    }

    /**
    * Compute facets (unique statuses + priorities) across the ENTIRE result set.
    *
    * NOTE: bounded by MAX_FACET_SCAN_ISSUES to keep this safe in large instances.
    */
    private Map<String, List<String>> computeFacets(ApplicationUser user, String facetsJql) throws SearchException {
        // Parse and validate facets JQL
        SearchService.ParseResult parseResult = searchService.parseQuery(user, facetsJql);
        if (!parseResult.isValid()) {
            throw new IllegalArgumentException("Invalid JQL query for facets: " + parseResult.getErrors());
        }
        
        Query query = parseResult.getQuery();

        TreeSet<String> statuses = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        TreeSet<String> priorities = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);

        int startAt = 0;
        int scanned = 0;
    while (scanned < MAX_FACET_SCAN_ISSUES) {
        int pageSize = Math.min(FACET_PAGE_SIZE, MAX_FACET_SCAN_ISSUES - scanned);
        PagerFilter pager = new PagerFilter(startAt, pageSize);
        SearchResults results = searchService.search(user, query, pager);
        Collection<Issue> issues = results.getResults();
        if (issues == null || issues.isEmpty()) {
            break;
        }

        for (Issue issue : issues) {
            if (issue == null) continue;
            if (issue.getStatus() != null && issue.getStatus().getName() != null) {
                statuses.add(issue.getStatus().getName());
            }
            Priority pr = issue.getPriority();
            if (pr != null && pr.getName() != null) {
                priorities.add(pr.getName());
            }
        }

        int fetched = issues.size();
        scanned += fetched;
        startAt += fetched;
        
        // Stop if we've scanned the full result set.
        if (startAt >= results.getTotal()) {
            break;
        }
        
        // Stop if pager returned fewer than requested (no more pages).
        if (fetched < pageSize) {
            break;
        }
    }

    Map<String, List<String>> facets = new HashMap<>();
    facets.put("statuses", new ArrayList<>(statuses));
    facets.put("priorities", new ArrayList<>(priorities));
    return facets;
}


    /**
     * Search issues for a specific project with current user filter
     *
     * @param projectKey The project key
     * @param startIndex The start index for pagination
     * @param pageSize The number of results per page
     * @return Search response with paginated results
     */
    public IssueSearchResponseDTO searchProjectIssues(String projectKey, int startIndex, int pageSize) {
        String jql = String.format("project = %s AND reporter = currentUser() ORDER BY created DESC", projectKey);
        IssueSearchResponseDTO response = searchIssues(jql, startIndex, pageSize);
        response.setProjectKey(projectKey);
        return response;
    }

    /**
     * Search issues for a specific project (all issues user can see)
     *
     * @param projectKey The project key
     * @param startIndex The start index for pagination
     * @param pageSize The number of results per page
     * @return Search response with paginated results
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
                    // ALL_ORGANIZATIONS_AND_GROUPS matches JSM portal visibility (reporter + org + shared groups)
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
     * Build enhanced JQL by appending search and filter clauses.
     * This enables server-side search/filtering across the ENTIRE result set,
     * not just the current page (which is the key fix for the JQL table issues).
     *
     * @param baseJql The original JQL query
     * @param searchTerm Text to search in summary, description, and key
     * @param statusFilter Comma-separated list of status names to filter by
     * @param priorityFilter Comma-separated list of priority names to filter by
     * @return Enhanced JQL with search/filter clauses appended
     */
    private String buildEnhancedJql(String baseJql, String searchTerm, String statusFilter, String priorityFilter) {
        if (baseJql == null || baseJql.trim().isEmpty()) {
            baseJql = "";
        }

        StringBuilder enhancedJql = new StringBuilder();

        // Extract ORDER BY clause if present (we need to append it at the end)
        String orderByClause = "";
        String jqlWithoutOrder = baseJql;
        int orderByIndex = baseJql.toUpperCase().lastIndexOf("ORDER BY");
        if (orderByIndex > 0) {
            orderByClause = baseJql.substring(orderByIndex);
            jqlWithoutOrder = baseJql.substring(0, orderByIndex).trim();
        }

        // Start with base JQL (without ORDER BY)
        if (!jqlWithoutOrder.isEmpty()) {
            enhancedJql.append("(").append(jqlWithoutOrder).append(")");
        }

        // Add text search clause (search in key, summary, and description)
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String trimmedSearch = searchTerm.trim();
            String sanitizedSearch = sanitizeJqlValue(trimmedSearch);
            if (enhancedJql.length() > 0) {
                enhancedJql.append(" AND ");
            }

            // Check if search term looks like an issue key (e.g., "WMPR-123" or "ABC-1")
            // Pattern: uppercase letters followed by dash and numbers
            boolean looksLikeIssueKey = trimmedSearch.matches("^[A-Za-z]+-\\d+$");

            if (looksLikeIssueKey) {
                // For issue keys, use exact match on key field (case-insensitive in Jira)
                enhancedJql.append("(key = \"").append(sanitizedSearch.toUpperCase()).append("\"")
                           .append(" OR summary ~ \"").append(sanitizedSearch).append("\"")
                           .append(" OR description ~ \"").append(sanitizedSearch).append("\")");
            } else {
                // For general text search, use "text" field which searches across multiple fields
                // The "text" field is more robust with special characters like dashes
                // Also add explicit key search for partial key matches
                enhancedJql.append("(text ~ \"").append(sanitizedSearch).append("\"")
                           .append(" OR key ~ \"").append(sanitizedSearch).append("\")");
            }
        }

        // Add status filter clause
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

        // Add priority filter clause
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

        // Append ORDER BY clause at the end
        if (!orderByClause.isEmpty()) {
            enhancedJql.append(" ").append(orderByClause);
        }

        String result = enhancedJql.toString().trim();
        return result.isEmpty() ? baseJql : result;
    }

    /**
     * Sanitize a value for safe use in JQL queries.
     * Escapes special characters to prevent JQL injection.
     */
    private String sanitizeJqlValue(String value) {
        if (value == null) return "";
        // Escape backslashes first, then quotes
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
     * Convert Jira Issue to DTO
     */
    private IssueDTO convertToDTO(Issue issue) {
        IssueDTO dto = new IssueDTO();
        
        // Basic issue information
        dto.setId(issue.getId().toString());
        dto.setKey(issue.getKey());
        dto.setSummary(issue.getSummary());
        dto.setDescription(issue.getDescription());
        
        // Project information
        Project project = issue.getProjectObject();
        if (project != null) {
            dto.setProjectKey(project.getKey());
            dto.setProjectName(project.getName());

            // --- ADDED: Resolve Service Desk ID for Portal Linking ---
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
            // ---------------------------------------------------------
        }
        
        // Status information
        if (issue.getStatus() != null) {
            dto.setStatus(issue.getStatus().getName());
            dto.setStatusId(issue.getStatus().getId());
            dto.setStatusIconUrl(issue.getStatus().getIconUrl());
            // Status category for badge coloring (new = To Do, indeterminate = In Progress, done = Done)
            if (issue.getStatus().getStatusCategory() != null) {
                dto.setStatusCategoryKey(issue.getStatus().getStatusCategory().getKey());
            }
        }
        
        // Priority information
        Priority priority = issue.getPriority();
        if (priority != null) {
            dto.setPriority(priority.getName());
            dto.setPriorityId(priority.getId());
            dto.setPriorityIconUrl(priority.getIconUrl());
        }
        
        // Issue type information
        if (issue.getIssueType() != null) {
            dto.setIssueType(issue.getIssueType().getName());
            dto.setIssueTypeId(issue.getIssueType().getId());
            dto.setIssueTypeIconUrl(issue.getIssueType().getIconUrl());
        }
        
        // Date information
        dto.setCreated(issue.getCreated());
        dto.setUpdated(issue.getUpdated());
        dto.setDueDate(issue.getDueDate());
        
        // Reporter information
        ApplicationUser reporter = issue.getReporter();
        if (reporter != null) {
            dto.setReporter(reporter.getKey());
            dto.setReporterDisplayName(reporter.getDisplayName());
            dto.setReporterEmailAddress(reporter.getEmailAddress());
            dto.setReporterAvatarUrl(getAvatarUrl(reporter));
        }
        
        // Assignee information
        ApplicationUser assignee = issue.getAssignee();
        if (assignee != null) {
            dto.setAssignee(assignee.getKey());
            dto.setAssigneeDisplayName(assignee.getDisplayName());
            dto.setAssigneeEmailAddress(assignee.getEmailAddress());
            dto.setAssigneeAvatarUrl(getAvatarUrl(assignee));
        }
        
        // Resolution information
        if (issue.getResolution() != null) {
            dto.setResolution(issue.getResolution().getName());
            dto.setResolutionId(issue.getResolution().getId());
            dto.setResolutionDate(issue.getResolutionDate());
        }
        
        // Labels
        if (issue.getLabels() != null && !issue.getLabels().isEmpty()) {
            dto.setLabels(issue.getLabels().stream()
                    .map(Object::toString)
                    .toArray(String[]::new));
        }

        // Components
        if (issue.getComponents() != null) {
            dto.setComponents(issue.getComponents().stream()
                    .map(component -> component.getName())
                    .toArray(String[]::new));
        }
        
        // Versions
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
        
        // Time tracking
        dto.setTimeOriginalEstimate(issue.getOriginalEstimate());
        dto.setTimeEstimate(issue.getEstimate());
        dto.setTimeSpent(issue.getTimeSpent());

        // Other fields
        dto.setEnvironment(issue.getEnvironment());
        dto.setVotes(issue.getVotes() != null ? issue.getVotes().intValue() : null);
        dto.setWatcherCount(issue.getWatches() != null ? issue.getWatches().intValue() : null);

        // Extract custom field values
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

    /**
     * Extract display value from custom field value
     * Handles various Jira custom field types including:
     * - String, Number, Date
     * - User Picker (single and multi)
     * - Select (single and multi)
     * - Cascading Select
     * - Labels
     * - JSM Assets/Objects
     */
    private String extractCustomFieldDisplayValue(com.atlassian.jira.issue.fields.CustomField cf, Object value) {
        try {
            if (value == null) {
                return null;
            }

            // Get custom field type key for type-specific handling
            String typeKey = "";
            if (cf.getCustomFieldType() != null) {
                typeKey = cf.getCustomFieldType().getKey();
            }

            // Handle common custom field types
            if (value instanceof String) {
                return (String) value;
            } else if (value instanceof Number) {
                return value.toString();
            } else if (value instanceof java.util.Date) {
                return new java.text.SimpleDateFormat("yyyy-MM-dd").format((java.util.Date) value);
            } else if (value instanceof com.atlassian.jira.issue.customfields.option.Option) {
                return ((com.atlassian.jira.issue.customfields.option.Option) value).getValue();
            } else if (value instanceof ApplicationUser) {
                // Single User Picker
                return ((ApplicationUser) value).getDisplayName();
            } else if (value instanceof java.util.Map) {
                // Handle Cascading Select (stored as Map<String, Option>)
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
                // Handle multi-select, multi-user, labels, assets, etc.
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
                // Fallback: Try to extract display value from object using reflection or toString
                return extractObjectDisplayValue(value);
            }
        } catch (Exception e) {
            log.debug("Error extracting display value for custom field {} (type: {}): {}",
                    cf.getId(), cf.getCustomFieldType() != null ? cf.getCustomFieldType().getKey() : "unknown", e.getMessage());
            return null;
        }
    }

    /**
     * Extract value from a collection item (for multi-value fields)
     */
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
            // Try common interface methods for Assets and other objects
            return extractObjectDisplayValue(item);
        }
    }

    /**
     * Extract display value from an unknown object type
     * Tries common method names used by Jira objects
     */
    private String extractObjectDisplayValue(Object obj) {
        if (obj == null) {
            return null;
        }

        // Try common method names that Jira objects typically have
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
                // Method doesn't exist, try next
            } catch (Exception e) {
                log.trace("Error invoking {} on {}: {}", methodName, obj.getClass().getSimpleName(), e.getMessage());
            }
        }

        // Last resort: toString, but only if it looks like a meaningful value
        String str = obj.toString();
        if (!str.contains("@") && str.length() < 500) {
            return str;
        }

        log.debug("Could not extract display value from object of type: {}", obj.getClass().getName());
        return null;
    }

    /**
     * Get user avatar URL
     */
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

    /**
     * Create an empty response for error cases
     */
    private IssueSearchResponseDTO createEmptyResponse(String jqlQuery, int startIndex, int pageSize) {
        IssueSearchResponseDTO response = new IssueSearchResponseDTO(
                Collections.emptyList(), 0, startIndex, pageSize
        );
        response.setJqlQuery(jqlQuery);
        response.setExecutionTimeMs(0);
        return response;
    }

    /**
     * Validate if user has permission to see issues in project
     */
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

            // Try a simple search to check permissions
            String testJql = String.format("project = %s", projectKey);
            SearchService.ParseResult parseResult = searchService.parseQuery(currentUser, testJql);
            return parseResult.isValid();

        } catch (Exception e) {
            log.debug("Error checking project permissions for {}: {}", projectKey, e.getMessage());
            return false;
        }
    }
}
