package com.samsungbuilder.jsm.dto;

import java.util.List;

/**
 * Response DTO for Issue Search operations
 * Contains paginated issue results and metadata
 */
public class IssueSearchResponseDTO {
    private List<IssueDTO> issues;
    private int totalCount;
    private int startIndex;
    private int pageSize;
    private boolean hasNextPage;
    private boolean hasPreviousPage;
    private String jqlQuery;
    private String projectKey;
    private long executionTimeMs;

    // User context for debugging permission issues
    private String searchedAsUserKey;
    private String searchedAsUserName;
    private String searchedAsUserDisplayName;
    private String resolvedJqlQuery; // The JQL after currentUser() is resolved

    // ✅ NEW: Facets for filters (computed across the FULL result set, not just current page)
    private Facets facets;

    public static class Facets {
        private List<String> statuses;
        private List<String> priorities;

        public Facets() {}

        public Facets(List<String> statuses, List<String> priorities) {
            this.statuses = statuses;
            this.priorities = priorities;
        }

        public List<String> getStatuses() { return statuses; }
        public void setStatuses(List<String> statuses) { this.statuses = statuses; }

        public List<String> getPriorities() { return priorities; }
        public void setPriorities(List<String> priorities) { this.priorities = priorities; }
    }

    public IssueSearchResponseDTO() {
    }

    public IssueSearchResponseDTO(List<IssueDTO> issues, int totalCount, int startIndex, int pageSize) {
        this.issues = issues;
        this.totalCount = totalCount;
        this.startIndex = startIndex;
        this.pageSize = pageSize;
        this.hasNextPage = (startIndex + pageSize) < totalCount;
        this.hasPreviousPage = startIndex > 0;
    }

    // Getters and Setters
    public List<IssueDTO> getIssues() { return issues; }
    public void setIssues(List<IssueDTO> issues) { this.issues = issues; }

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }

    public int getStartIndex() { return startIndex; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }

    public boolean isHasNextPage() { return hasNextPage; }
    public void setHasNextPage(boolean hasNextPage) { this.hasNextPage = hasNextPage; }

    public boolean isHasPreviousPage() { return hasPreviousPage; }
    public void setHasPreviousPage(boolean hasPreviousPage) { this.hasPreviousPage = hasPreviousPage; }

    public String getJqlQuery() { return jqlQuery; }
    public void setJqlQuery(String jqlQuery) { this.jqlQuery = jqlQuery; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }

    public String getSearchedAsUserKey() { return searchedAsUserKey; }
    public void setSearchedAsUserKey(String searchedAsUserKey) { this.searchedAsUserKey = searchedAsUserKey; }

    public String getSearchedAsUserName() { return searchedAsUserName; }
    public void setSearchedAsUserName(String searchedAsUserName) { this.searchedAsUserName = searchedAsUserName; }

    public String getSearchedAsUserDisplayName() { return searchedAsUserDisplayName; }
    public void setSearchedAsUserDisplayName(String searchedAsUserDisplayName) { this.searchedAsUserDisplayName = searchedAsUserDisplayName; }

    public String getResolvedJqlQuery() { return resolvedJqlQuery; }
    public void setResolvedJqlQuery(String resolvedJqlQuery) { this.resolvedJqlQuery = resolvedJqlQuery; }

    public Facets getFacets() { return facets; }
    public void setFacets(Facets facets) { this.facets = facets; }

    public int getCurrentPage() {
        return pageSize > 0 ? (startIndex / pageSize) + 1 : 1;
    }

    public int getTotalPages() {
        return pageSize > 0 ? (int) Math.ceil((double) totalCount / pageSize) : 1;
    }
}
