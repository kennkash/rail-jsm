 /**
     * Convert IssueSearchResponseDTO to Map to bypass JAX-RS Jackson serialization issues.
     * Mirrors the PortalConfigDTO workaround so the frontend always receives a stable JSON shape.
     */
    private Map<String, Object> convertIssueSearchResponseToMap(IssueSearchResponseDTO dto) {
        Map<String, Object> map = new HashMap<>();

        List<Map<String, Object>> issueMaps = dto.getIssues() != null
                ? dto.getIssues().stream()
                        .map(this::convertIssueDtoToMap)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        map.put("issues", issueMaps);
        map.put("totalCount", dto.getTotalCount());
        map.put("startIndex", dto.getStartIndex());
        map.put("pageSize", dto.getPageSize());
        map.put("hasNextPage", dto.isHasNextPage());
        map.put("hasPreviousPage", dto.isHasPreviousPage());
        map.put("jqlQuery", dto.getJqlQuery());
        map.put("projectKey", dto.getProjectKey());
        map.put("executionTimeMs", dto.getExecutionTimeMs());
        map.put("currentPage", dto.getCurrentPage());
        map.put("totalPages", dto.getTotalPages());

        // User context for debugging permission issues
        map.put("searchedAsUserKey", dto.getSearchedAsUserKey());
        map.put("searchedAsUserName", dto.getSearchedAsUserName());
        map.put("searchedAsUserDisplayName", dto.getSearchedAsUserDisplayName());
        map.put("resolvedJqlQuery", dto.getResolvedJqlQuery());

        return map;
    }
