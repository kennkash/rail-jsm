*** Begin Patch
*** Update File: rail-at-sas/frontend/components/landing/standalone-jql-table.tsx
@@
-import { ArrowUpDown, Search, Filter, ExternalLink, Loader2 } from "lucide-react";
+import { ArrowUp, ArrowDown, ArrowUpDown, Search, Filter, ExternalLink, Loader2 } from "lucide-react";
@@
 export function StandaloneJQLTable({
@@
   const serverStatusFilter = statusFilter.length > 0 ? statusFilter.join(",") : undefined;
   const serverPriorityFilter = priorityFilter.length > 0 ? priorityFilter.join(",") : undefined;
 
   // Fetch issues using JQL with server-side search and filtering
   const { data, isLoading, error, isFetching } = useJQLSearch(
     jqlQuery,
     {
       startIndex: currentPage * pageSize,
       pageSize,
       enabled: true,
       searchTerm: debouncedSearchTerm || undefined,
       statusFilter: serverStatusFilter,
       priorityFilter: serverPriorityFilter,
+      // Server-side sorting (applies to ENTIRE result set)
+      sortField: sortColumn || undefined,
+      sortDir: sortDirection,
+      // Include facets so filter dropdowns show ALL available values (across full match-set)
+      includeFacets: true,
     }
   );
@@
   const issues = data?.issues || [];
 
   const availableStatuses = useMemo(
-    () =>
-      Array.from(
-        new Set(
-          issues
-            .map((issue) => issue.status)
-            .filter((status): status is string => Boolean(status)),
-        ),
-      ),
-    [issues],
+    () => {
+      // Prefer server facets (full JQL match-set), fallback to current page values
+      const facetStatuses = data?.facets?.statuses;
+      if (Array.isArray(facetStatuses) && facetStatuses.length > 0) {
+        return facetStatuses;
+      }
+      return Array.from(
+        new Set(
+          issues
+            .map((issue) => issue.status)
+            .filter((status): status is string => Boolean(status)),
+        ),
+      );
+    },
+    [data, issues],
   );
 
   const availablePriorities = useMemo(
-    () =>
-      Array.from(
-        new Set(
-          issues
-            .map((issue) => issue.priority)
-            .filter((priority): priority is string => Boolean(priority)),
-        ),
-      ),
-    [issues],
+    () => {
+      // Prefer server facets (full JQL match-set), fallback to current page values
+      const facetPriorities = data?.facets?.priorities;
+      if (Array.isArray(facetPriorities) && facetPriorities.length > 0) {
+        return facetPriorities;
+      }
+      return Array.from(
+        new Set(
+          issues
+            .map((issue) => issue.priority)
+            .filter((priority): priority is string => Boolean(priority)),
+        ),
+      );
+    },
+    [data, issues],
   );
@@
   const hasActiveFilters = statusFilter.length > 0 || priorityFilter.length > 0;
 
   const handleSort = (columnId: string) => {
     if (sortColumn === columnId) {
       setSortDirection(sortDirection === "asc" ? "desc" : "asc");
     } else {
       setSortColumn(columnId);
       setSortDirection("asc");
     }
+    // Sorting changes affect ordering across entire set; reset to first page
+    setCurrentPage(0);
   };
 
+  /**
+   * Returns the appropriate aria-sort value for a column header.
+   * Used for accessibility to indicate current sort state.
+   */
+  const getAriaSort = (columnId: string): "none" | "ascending" | "descending" => {
+    if (sortColumn !== columnId) return "none";
+    return sortDirection === "asc" ? "ascending" : "descending";
+  };
+
   const handlePageChange = (newPage: number) => {
     setCurrentPage(newPage);
   };
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
+  /**
+   * Server-side sorting applies across the ENTIRE result set (via sortField/sortDir),
+   * so we do NOT sort client-side here.
+   */
+  const displayIssues = issues;
@@
       {/* Results count */}
       {!isLoading && !error && (
         <div className="text-sm text-muted-foreground flex items-center justify-between">
           <span>
-            Showing {sortedIssues.length} {sortedIssues.length === 1 ? "result" : "results"}
-            {data?.totalCount && data.totalCount > sortedIssues.length && (
+            Showing {displayIssues.length} {displayIssues.length === 1 ? "result" : "results"}
+            {data?.totalCount && data.totalCount > displayIssues.length && (
               <span> of {data.totalCount} total</span>
             )}
             {(debouncedSearchTerm || hasActiveFilters) && (
               <span className="ml-1">(filtered)</span>
             )}
           </span>
         </div>
       )}
@@
       {/* Table */}
       {!isLoading && !error && (
         <div className="border border-border rounded-lg overflow-hidden bg-card">
           <Table>
             <TableHeader>
               <TableRow className="bg-muted hover:bg-muted">
                 {columns.map((column) => (
                   <TableHead key={column.id}>
                     <Button
                       variant="ghost"
                       size="sm"
-                      className="h-auto p-0 hover:bg-transparent font-semibold"
+                      aria-sort={getAriaSort(column.id)}
+                      className={cn(
+                        "group h-auto p-0 hover:bg-transparent font-semibold",
+                        "inline-flex items-center justify-start gap-2",
+                        "cursor-pointer select-none"
+                      )}
                       onClick={() => handleSort(column.id)}
                     >
-                      {column.name}
-                      <ArrowUpDown className="ml-2 h-3 w-3" />
+                      <span className="group-hover:underline">{column.name}</span>
+
+                      {sortColumn === column.id ? (
+                        sortDirection === "asc" ? (
+                          <ArrowUp className="h-3 w-3" aria-label="Sorted ascending" />
+                        ) : (
+                          <ArrowDown className="h-3 w-3" aria-label="Sorted descending" />
+                        )
+                      ) : (
+                        <ArrowUpDown
+                          className="h-3 w-3 opacity-0 transition-opacity group-hover:opacity-40"
+                          aria-hidden="true"
+                        />
+                      )}
                     </Button>
                   </TableHead>
                 ))}
                 <TableHead className="w-10"></TableHead>
               </TableRow>
             </TableHeader>
             <TableBody>
-              {sortedIssues.length === 0 ? (
+              {displayIssues.length === 0 ? (
                 <TableRow>
                   <TableCell colSpan={columns.length + 1} className="text-center py-8 text-muted-foreground">
                     {debouncedSearchTerm || hasActiveFilters ? (
                       <div className="space-y-2">
@@
                   </TableCell>
                 </TableRow>
               ) : (
-                sortedIssues.map((issue) => (
+                displayIssues.map((issue) => (
                   <TableRow key={issue.key} className="hover:bg-muted transition-colors">
                     {columns.map((column) => (
                       <TableCell key={column.id}>
*** End Patch
