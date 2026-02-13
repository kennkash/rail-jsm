
    // ==================== Issue Search Endpoints ====================

    /**
     * Search issues using JQL query with optional server-side search and filter.
     *
     * Server-side search/filter enables searching across the ENTIRE result set,
     * not just the current page. This fixes the issue where search only worked
     * on visible issues.
     *
     * GET /rest/rail/1.0/issues/search?jql=query&start=0&limit=25&search=term&status=Open,Done&priority=High
     *
     * @param jqlQuery The JQL query (required)
     * @param startIndex Pagination start index (default: 0)
     * @param pageSize Number of results per page (default: 25)
     * @param searchTerm Optional text search (searches key, summary, description)
     * @param statusFilter Optional comma-separated status names to filter by
     * @param priorityFilter Optional comma-separated priority names to filter by
     */
    @GET
    @Path("issues/search")
    public Response searchIssues(
            @QueryParam("jql") String jqlQuery,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize,
            @QueryParam("search") String searchTerm,
            @QueryParam("status") String statusFilter,
            @QueryParam("priority") String priorityFilter) {
        log.debug("GET /issues/search?jql={}&start={}&limit={}&search={}&status={}&priority={}",
                  jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);

        if (jqlQuery == null || jqlQuery.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("JQL query parameter is required"))
                    .build();
        }

        try {
            IssueSearchResponseDTO response = issueService.searchIssues(
                    jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid JQL query: {}", jqlQuery, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Invalid JQL query: " + e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Error searching issues with JQL: {}", jqlQuery, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Issue search failed: " + e.getMessage()))
                    .build();
        }
    }
