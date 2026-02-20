*** Begin Patch
*** Update File: rail-at-sas/frontend/components/landing/standalone-jql-table.tsx
@@
   // Build server-side filter params (comma-separated strings)
   const serverStatusFilter = statusFilter.length > 0 ? statusFilter.join(",") : undefined;
   const serverPriorityFilter = priorityFilter.length > 0 ? priorityFilter.join(",") : undefined;
 
-  // Fetch issues using JQL with server-side search and filtering
-  const { data, isLoading, error, isFetching } = useJQLSearch(
-    jqlQuery,
-    {
-      startIndex: currentPage * pageSize,
-      pageSize,
-      enabled: true,
-      searchTerm: debouncedSearchTerm || undefined,
-      statusFilter: serverStatusFilter,
-      priorityFilter: serverPriorityFilter,
-      // Server-side sorting (applies to ENTIRE result set)
-      sortField: sortColumn || undefined,
-      sortDir: sortDirection,
-      // Include facets so filter dropdowns show ALL available values (across full match-set)
-      includeFacets: true,
-    }
-  );
+  // ========================
+  // JQL Issues Query (paged)
+  // ========================
+  // NOTE: Do NOT include facets here, otherwise paging/sorting/filtering
+  // would recompute facets on every interaction.
+  const { data, isLoading, error, isFetching } = useJQLSearch(jqlQuery, {
+    startIndex: currentPage * pageSize,
+    pageSize,
+    enabled: true,
+    searchTerm: debouncedSearchTerm || undefined,
+    statusFilter: serverStatusFilter,
+    priorityFilter: serverPriorityFilter,
+    // Server-side sorting (applies to ENTIRE result set)
+    sortField: sortColumn || undefined,
+    sortDir: sortDirection,
+    includeFacets: false,
+  });
+
+  // =========================
+  // JQL Facets Query (stable)
+  // =========================
+  // Compute facets once per (jqlQuery + searchTerm). Do NOT apply filters/sort.
+  const { data: facetsData, isLoading: isFacetsLoading } = useJQLSearch(jqlQuery, {
+    startIndex: 0,
+    pageSize: 1, // minimal: we only need facets
+    enabled: Boolean(showFilter),
+    searchTerm: debouncedSearchTerm || undefined,
+    includeFacets: true,
+  });
@@
   const issues = data?.issues || [];
 
   const availableStatuses = useMemo(
     () => {
       // Prefer server facets (full JQL match-set), fallback to current page values
-      const facetStatuses = data?.facets?.statuses;
+      const facetStatuses = facetsData?.facets?.statuses;
       if (Array.isArray(facetStatuses) && facetStatuses.length > 0) {
         return facetStatuses;
       }
       return Array.from(
         new Set(
@@
     },
-    [data, issues],
+    [facetsData, issues],
   );
 
   const availablePriorities = useMemo(
     () => {
       // Prefer server facets (full JQL match-set), fallback to current page values
-      const facetPriorities = data?.facets?.priorities;
+      const facetPriorities = facetsData?.facets?.priorities;
       if (Array.isArray(facetPriorities) && facetPriorities.length > 0) {
         return facetPriorities;
       }
       return Array.from(
         new Set(
@@
     },
-    [data, issues],
+    [facetsData, issues],
   );
@@
-  // Get sortable value for issue field
-  const getIssueFieldValueForSort = (issue: Issue, columnId: string): string | number | undefined => {
-    switch (columnId) {
-      case "key":
-        return issue.key;
-      case "summary":
-        return issue.summary;
-      case "status":
-        return issue.status;
-      case "priority":
-        return issue.priority;
-      case "issueType":
-        return issue.issueType;
-      case "created":
-        return new Date(issue.created).getTime();
-      case "updated":
-        return issue.updated ? new Date(issue.updated).getTime() : undefined;
-      case "dueDate":
-        return issue.dueDate ? new Date(issue.dueDate).getTime() : undefined;
-      case "reporter":
-        return issue.reporterDisplayName || issue.reporter;
-      case "assignee":
-        return issue.assigneeDisplayName || issue.assignee;
-      case "resolution":
-        return issue.resolution;
-      default:
-        // Custom field
-        if (columnId.startsWith("customfield_") && issue.customFields) {
-          return issue.customFields[columnId] || undefined;
-        }
-        return undefined;
-    }
-  };
-
   /**
   * Server-side sorting applies across the ENTIRE result set (via sortField/sortDir),
   * so we do NOT sort client-side here.
   */
   const displayIssues = issues;
@@
-  // Sort issues client-side (search and filter are server-side)
-  const sortedIssues = useMemo(() => {
-    if (!sortColumn) {
-      return issues;
-    }
-
-    return [...issues].sort((a, b) => {
-      const aVal = getIssueFieldValueForSort(a, sortColumn);
-      const bVal = getIssueFieldValueForSort(b, sortColumn);
-
-      if (!aVal && !bVal) return 0;
-      if (!aVal) return 1;
-      if (!bVal) return -1;
-
-      const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
-      return sortDirection === "asc" ? comparison : -comparison;
-    });
-  }, [issues, sortColumn, sortDirection]);
+  // NOTE: Client-side sorting intentionally removed. Sorting is handled server-side.
*** End Patch
