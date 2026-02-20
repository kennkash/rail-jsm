// ========================
// JQL Issues Query (paged)
// ========================
const {
  data: jqlData,
  isLoading: isJqlLoading,
  error: jqlError,
  isFetching: isJqlFetching,
} = useJQLSearch(shouldUseJQL ? resolvedJqlQuery : null, {
  startIndex: currentPage * pageSize,
  pageSize,
  enabled: shouldUseJQL,
  // Server-side search and filter params
  searchTerm: debouncedSearchTerm || undefined,
  statusFilter: serverStatusFilter,
  priorityFilter: serverPriorityFilter,
  // Server-side sorting (applies to ENTIRE result set)
  sortField: sortColumn || undefined,
  sortDir: sortDirection,
  // IMPORTANT: do NOT include facets here (avoid recomputing on every page/sort/filter)
  includeFacets: false,
});

// ========================
// JQL Facets Query (stable)
// ========================
// Compute facets across the full JQL match-set once per (JQL + searchTerm).
// Do NOT include status/priority filters or sorting; we want full dropdown options.
const {
  data: facetsData,
  isLoading: isFacetsLoading,
  error: facetsError,
} = useJQLSearch(shouldUseJQL ? resolvedJqlQuery : null, {
  startIndex: 0,
  pageSize: 1, // minimal: we only need facets
  enabled: shouldUseJQL && showFilter, // only when filter UI exists
  searchTerm: debouncedSearchTerm || undefined,
  includeFacets: true,
});

const availableStatuses = useMemo(() => {
  // Prefer facets query (full JQL match-set), fallback to issues response, then current page values
  const facetStatuses =
    facetsData?.facets?.statuses ??
    (shouldUseJQL ? jqlData?.facets?.statuses : undefined);

  if (Array.isArray(facetStatuses) && facetStatuses.length > 0) {
    return facetStatuses;
  }

  return Array.from(
    new Set(
      issues
        .map((issue) => issue.status)
        .filter((status): status is string => Boolean(status)),
    ),
  );
}, [facetsData, shouldUseJQL, jqlData, issues]);

const availablePriorities = useMemo(() => {
  // Prefer facets query (full JQL match-set), fallback to issues response, then current page values
  const facetPriorities =
    facetsData?.facets?.priorities ??
    (shouldUseJQL ? jqlData?.facets?.priorities : undefined);

  if (Array.isArray(facetPriorities) && facetPriorities.length > 0) {
    return facetPriorities;
  }

  return Array.from(
    new Set(
      issues
        .map((issue) => issue.priority)
        .filter((priority): priority is string => Boolean(priority)),
    ),
  );
}, [facetsData, shouldUseJQL, jqlData, issues]);


