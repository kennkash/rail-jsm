// /rail-at-sas/frontend/components/landing/standalone-jql-table.tsx

/**
 * StandaloneJQLTable Component
 *
 * A standalone version of the JQL Table component that doesn't require FormComponentModel.
 * Extracted from portal-builder/form-components/portal-jql-table.tsx for use in the landing page.
 *
 * Features:
 * - JQL query execution with server-side search and filtering
 * - Status/Priority badges with color coding
 * - Column sorting
 * - Pagination
 * - Empty state handling
 * - Smart linking: Redirects to Customer Portal if serviceDeskId is present
 */

import { useState, useMemo, useEffect } from "react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";
import { ArrowUpDown, Search, Filter, ExternalLink, Loader2 } from "lucide-react";
import { cn } from "@/lib/utils";
import { useJQLSearch } from "@/hooks/use-issues";
import { Issue } from "@/lib/api/issues-client";
import type { StandaloneJQLTableProps, ColumnConfig } from "@/types/landing.types";

// Default columns if none provided
const defaultColumns: ColumnConfig[] = [
  { id: "key", name: "Key", isCustom: false },
  { id: "summary", name: "Summary", isCustom: false },
  { id: "status", name: "Status", isCustom: false },
  { id: "priority", name: "Priority", isCustom: false },
  { id: "created", name: "Created", isCustom: false },
];

export function StandaloneJQLTable({
  jqlQuery,
  title,
  subtitle,
  columns = defaultColumns,
  pageSize = 10,
  showSearch = true,
  showFilter = true,
}: StandaloneJQLTableProps) {
  const [searchInput, setSearchInput] = useState(""); // What user types (immediate)
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState(""); // Debounced value for API
  const [sortColumn, setSortColumn] = useState("");
  const [sortDirection, setSortDirection] = useState<"asc" | "desc">("asc");
  const [currentPage, setCurrentPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState<string[]>([]);
  const [priorityFilter, setPriorityFilter] = useState<string[]>([]);
  const [isFilterOpen, setIsFilterOpen] = useState(false);

  // Debounce search term to avoid excessive API calls (300ms delay)
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchTerm(searchInput);
      // Reset to first page when search changes
      if (searchInput !== debouncedSearchTerm) {
        setCurrentPage(0);
      }
    }, 300);
    return () => clearTimeout(timer);
  }, [searchInput, debouncedSearchTerm]);

  // Reset to first page when filters change
  useEffect(() => {
    setCurrentPage(0);
  }, [statusFilter, priorityFilter]);

  // Build server-side filter params (comma-separated strings)
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
    }
  );

  // Show loading indicator when fetching (includes refetches for search/filter)
  const isSearching = isFetching && debouncedSearchTerm !== "";

  const issues = data?.issues || [];

  const availableStatuses = useMemo(
    () =>
      Array.from(
        new Set(
          issues
            .map((issue) => issue.status)
            .filter((status): status is string => Boolean(status)),
        ),
      ),
    [issues],
  );

  const availablePriorities = useMemo(
    () =>
      Array.from(
        new Set(
          issues
            .map((issue) => issue.priority)
            .filter((priority): priority is string => Boolean(priority)),
        ),
      ),
    [issues],
  );

  const hasActiveFilters = statusFilter.length > 0 || priorityFilter.length > 0;

  const handleSort = (columnId: string) => {
    if (sortColumn === columnId) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc");
    } else {
      setSortColumn(columnId);
      setSortDirection("asc");
    }
  };

  const handlePageChange = (newPage: number) => {
    setCurrentPage(newPage);
  };

  // Format date for display
  const formatDate = (dateString: string) => {
    try {
      return new Date(dateString).toLocaleDateString();
    } catch {
      return dateString;
    }
  };

  // Get sortable value for issue field
  const getIssueFieldValueForSort = (issue: Issue, columnId: string): string | number | undefined => {
    switch (columnId) {
      case "key":
        return issue.key;
      case "summary":
        return issue.summary;
      case "status":
        return issue.status;
      case "priority":
        return issue.priority;
      case "issueType":
        return issue.issueType;
      case "created":
        return new Date(issue.created).getTime();
      case "updated":
        return issue.updated ? new Date(issue.updated).getTime() : undefined;
      case "dueDate":
        return issue.dueDate ? new Date(issue.dueDate).getTime() : undefined;
      case "reporter":
        return issue.reporterDisplayName || issue.reporter;
      case "assignee":
        return issue.assigneeDisplayName || issue.assignee;
      case "resolution":
        return issue.resolution;
      default:
        // Custom field
        if (columnId.startsWith("customfield_") && issue.customFields) {
          return issue.customFields[columnId] || undefined;
        }
        return undefined;
    }
  };

  // Get display value for issue field
  const getIssueFieldValue = (issue: Issue, column: ColumnConfig): string => {
    const columnId = column.id;

    // Handle custom fields
    if (column.isCustom || columnId.startsWith("customfield_")) {
      return issue.customFields?.[columnId] || "";
    }

    // Handle system fields
    switch (columnId) {
      case "key":
        return issue.key || "";
      case "summary":
        return issue.summary || "";
      case "status":
        return issue.status || "";
      case "priority":
        return issue.priority || "";
      case "issueType":
        return issue.issueType || "";
      case "created":
        return formatDate(issue.created);
      case "updated":
        return issue.updated ? formatDate(issue.updated) : "";
      case "dueDate":
        return issue.dueDate ? formatDate(issue.dueDate) : "";
      case "reporter":
        return issue.reporterDisplayName || issue.reporter || "Unassigned";
      case "assignee":
        return issue.assigneeDisplayName || issue.assignee || "Unassigned";
      case "resolution":
        return issue.resolution || "";
      case "labels":
        return issue.labels?.join(", ") || "";
      case "components":
        return issue.components?.join(", ") || "";
      case "fixVersions":
        return issue.fixVersions?.join(", ") || "";
      case "affectedVersions":
        return issue.affectedVersions?.join(", ") || "";
      default:
        return "";
    }
  };

  // Sort issues client-side (search and filter are server-side)
  const sortedIssues = useMemo(() => {
    if (!sortColumn) {
      return issues;
    }

    return [...issues].sort((a, b) => {
      const aVal = getIssueFieldValueForSort(a, sortColumn);
      const bVal = getIssueFieldValueForSort(b, sortColumn);

      if (!aVal && !bVal) return 0;
      if (!aVal) return 1;
      if (!bVal) return -1;

      const comparison = aVal < bVal ? -1 : aVal > bVal ? 1 : 0;
      return sortDirection === "asc" ? comparison : -comparison;
    });
  }, [issues, sortColumn, sortDirection]);

  /**
   * Builds the issue URL.
   * Uses the Customer Portal URL if a serviceDeskId is available.
   * Falls back to the standard Jira browse URL otherwise.
   */
  const buildIssueUrl = (issue: Issue): string | null => {
    if (!issue.key) return null;

    // Using 'as any' here to access serviceDeskId if the Issue type hasn't been strictly updated yet
    const serviceDeskId = issue.serviceDeskId

    if (serviceDeskId) {
      return `/servicedesk/customer/portal/${serviceDeskId}/${issue.key}`;
    }

    return `/browse/${issue.key}`;
  };

  // Status category colors based on Jira Data Center standard categories
  const statusCategoryColors: Record<string, string> = {
    "new": "bg-muted text-muted-foreground border-border",
    "indeterminate": "bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-800",
    "done": "bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800",
    "negative": "bg-destructive/10 text-destructive border-destructive/20 dark:bg-destructive/30 dark:text-destructive/70 dark:border-destructive/80",
  };

  // Comprehensive status name to category mapping derived from Jira Data Center
  const statusNameToCategory: Record<string, string> = {
    "1d approval": "indeterminate",
    "1d follow up": "indeterminate",
    "24hr follow up": "indeterminate",
    "2wk approval": "indeterminate",
    "2wk monitoring": "indeterminate",
    "48hr approval": "indeterminate",
    "48hr follow up": "indeterminate",
    "4wk approval": "indeterminate",
    "4wk monitoring": "indeterminate",
    "accepted": "done",
    "accepted for innovation quarterly request": "indeterminate",
    "acknowledged": "new",
    "action request": "indeterminate",
    "activated": "done",
    "active": "indeterminate",
    "admin approved": "indeterminate",
    "admin declined": "indeterminate",
    "analyze": "indeterminate",
    "approval": "done",
    "approve for por": "indeterminate",
    "approved": "done",
    "approved for development": "new",
    "apr": "indeterminate",
    "arb": "indeterminate",
    "arb approved": "indeterminate",
    "arb decision": "indeterminate",
    "arb decision follow-up": "new",
    "arb rejected": "done",
    "archived": "done",
    "assigned": "new",
    "at risk": "indeterminate",
    "ats complete": "indeterminate",
    "awaiting cab approval": "indeterminate",
    "awaiting implementation": "indeterminate",
    "awp": "indeterminate",
    "backlog": "new",
    "basic pumi": "indeterminate",
    "beta": "indeterminate",
    "blocked": "indeterminate",
    "blocker": "new",
    "canceled": "done",
    "cancelled": "done",
    "change point execution": "indeterminate",
    "change verified": "done",
    "cip reflection": "indeterminate",
    "claim: done": "done",
    "claim: in progress": "indeterminate",
    "close by justification": "done",
    "closed": "done",
    "closed by justification": "done",
    "code complete": "indeterminate",
    "code review": "indeterminate",
    "complete": "done",
    "complete pending verification": "indeterminate",
    "completed": "done",
    "confirmed": "indeterminate",
    "content approval": "indeterminate",
    "control": "indeterminate",
    "cost signoff": "indeterminate",
    "cpm complete": "done",
    "cpm completed": "done",
    "csi: done": "indeterminate",
    "csi: in progress": "indeterminate",
    "customer replied": "indeterminate",
    "da approval": "indeterminate",
    "declined": "done",
    "define": "new",
    "delayed indefinitely": "done",
    "deleted": "done",
    "delivered": "indeterminate",
    "deploy": "indeterminate",
    "deployed": "indeterminate",
    "design": "indeterminate",
    "design finding": "indeterminate",
    "developer verified": "indeterminate",
    "development": "indeterminate",
    "development done": "indeterminate",
    "development queue (local)": "new",
    "development queue (overseas)": "new",
    "di pre-screen": "indeterminate",
    "di pre-screen follow-up": "new",
    "director approval": "indeterminate",
    "documentation": "indeterminate",
    "done": "done",
    "done with follow-up": "done",
    "draft": "new",
    "dropped": "done",
    "dsk - fbi approval": "indeterminate",
    "dsk - fbi researching": "indeterminate",
    "dsk-fbi queue": "new",
    "engineer review": "indeterminate",
    "error": "indeterminate",
    "escalate": "indeterminate",
    "escalated": "indeterminate",
    "execution pumi": "indeterminate",
    "external review": "indeterminate",
    "fabrication": "indeterminate",
    "fail": "indeterminate",
    "field finding": "indeterminate",
    "final approval": "indeterminate",
    "final metrology approval": "indeterminate",
    "follow-up needed": "indeterminate",
    "follow-up review": "indeterminate",
    "future consideration": "done",
    "gather requirements (local)": "indeterminate",
    "gather requirements (overseas)": "indeterminate",
    "gathering interest": "new",
    "gcs approved": "indeterminate",
    "gpm completed/pending ccs": "indeterminate",
    "gpm completed/pending fem": "indeterminate",
    "h/w setup": "indeterminate",
    "held": "indeterminate",
    "hold": "new",
    "hold - mfg e-type scrap stop": "indeterminate",
    "hold - pending shipping schedule": "indeterminate",
    "hold on s1/other member/group": "indeterminate",
    "holding on other group": "indeterminate",
    "hq review": "indeterminate",
    "idea": "new",
    "implementation": "indeterminate",
    "implementing": "indeterminate",
    "improve": "indeterminate",
    "in development": "indeterminate",
    "in maintenance": "indeterminate",
    "in pccb/submitted": "indeterminate",
    "in progress": "indeterminate",
    "in progress - korea": "indeterminate",
    "in review": "indeterminate",
    "in test": "indeterminate",
    "initial review": "new",
    "initiation": "new",
    "inno review": "indeterminate",
    "internal review": "indeterminate",
    "irb decision": "indeterminate",
    "issue blocked": "new",
    "it final review": "indeterminate",
    "it questionnaires": "indeterminate",
    "iwd": "indeterminate",
    "iwr": "indeterminate",
    "kick-off meeting": "indeterminate",
    "layer owner review": "indeterminate",
    "legal": "indeterminate",
    "live": "done",
    "long term": "indeterminate",
    "lvl 1 approval": "indeterminate",
    "lvl 2 approval": "indeterminate",
    "lvl 3 approval": "indeterminate",
    "lvl 4 approval": "indeterminate",
    "lvl 5 approval": "indeterminate",
    "m1": "new",
    "m2": "new",
    "m3": "new",
    "m4": "indeterminate",
    "m5": "indeterminate",
    "m6": "indeterminate",
    "manager approval": "indeterminate",
    "manager approved": "indeterminate",
    "measure": "indeterminate",
    "meeting": "indeterminate",
    "metrology approval": "indeterminate",
    "mfg approval": "new",
    "migrate complete": "done",
    "migrate in progress": "indeterminate",
    "mobile development": "indeterminate",
    "monitoring": "indeterminate",
    "new": "new",
    "new item": "new",
    "new request": "new",
    "next priority": "new",
    "not started": "new",
    "notification": "indeterminate",
    "npo": "indeterminate",
    "obsolete": "done",
    "off tool": "indeterminate",
    "on hold": "indeterminate",
    "on tool": "indeterminate",
    "ongoing effort": "indeterminate",
    "open": "new",
    "open event": "new",
    "operational": "indeterminate",
    "overwrite": "indeterminate",
    "owner complete": "indeterminate",
    "partial rollback": "done",
    "pause for higher priority": "indeterminate",
    "paused": "indeterminate",
    "peer review": "indeterminate",
    "peer review / change manager approval": "new",
    "peer_review": "new",
    "pending": "indeterminate",
    "pending alignment": "indeterminate",
    "pending approval": "new",
    "pending confirm": "new",
    "pending cpm": "indeterminate",
    "pending decision": "indeterminate",
    "pending development": "indeterminate",
    "pending director approval": "new",
    "pending execution": "indeterminate",
    "pending gh development": "indeterminate",
    "pending info": "indeterminate",
    "pending manager approval": "new",
    "pending owner": "new",
    "pending pl approval": "new",
    "pending release": "indeterminate",
    "pending reversion": "indeterminate",
    "pending review": "indeterminate",
    "pending roi": "indeterminate",
    "pending supervisor approval": "new",
    "pending system change": "indeterminate",
    "pendingdrop": "indeterminate",
    "pi approval": "indeterminate",
    "pi_verification": "new",
    "pie approval": "indeterminate",
    "plan": "indeterminate",
    "planning": "indeterminate",
    "po": "indeterminate",
    "post release": "indeterminate",
    "pra": "indeterminate",
    "pre-approval": "indeterminate",
    "pre-screen": "new",
    "pre-screen follow-up": "new",
    "procurement decision": "indeterminate",
    "procurement follow-up": "new",
    "project initiation": "new",
    "proposal": "new",
    "qa": "indeterminate",
    "qa approval": "indeterminate",
    "qr review": "indeterminate",
    "questionnaire": "new",
    "queue": "new",
    "rce: done": "indeterminate",
    "rce: in progress": "indeterminate",
    "rci: done": "indeterminate",
    "rci: in progress": "indeterminate",
    "ready": "new",
    "ready for release": "indeterminate",
    "ready for review": "indeterminate",
    "ready to delete": "indeterminate",
    "ready to migrate": "indeterminate",
    "recurring": "indeterminate",
    "rejected": "done",
    "release complete": "indeterminate",
    "released": "done",
    "reopened": "new",
    "request complete": "done",
    "request to close": "done",
    "request updated by requestor": "indeterminate",
    "researching": "indeterminate",
    "resolved": "done",
    "results and follow-up": "indeterminate",
    "retired": "done",
    "review": "indeterminate",
    "review with tr before complete": "indeterminate",
    "review/researching": "indeterminate",
    "rework": "indeterminate",
    "roadblock - on hold": "new",
    "roi submitted": "new",
    "rollback": "done",
    "s/w implementation": "indeterminate",
    "s1 approval": "indeterminate",
    "s2 approval": "indeterminate",
    "s2 standardization": "indeterminate",
    "safety": "indeterminate",
    "sccb (local)": "new",
    "sccb (overseas)": "new",
    "scheduled": "new",
    "scope commit": "indeterminate",
    "selected for development": "new",
    "selected for work": "new",
    "sent to fst": "indeterminate",
    "sent to jira": "new",
    "stage 1": "indeterminate",
    "stage 2": "indeterminate",
    "stage 3": "indeterminate",
    "stage 4": "indeterminate",
    "stakeholder": "indeterminate",
    "stop": "done",
    "submit to korea": "indeterminate",
    "submitted": "new",
    "supervisor approval": "indeterminate",
    "team approved": "indeterminate",
    "testing": "indeterminate",
    "tfd": "indeterminate",
    "ticket hold": "indeterminate",
    "ticket submit": "indeterminate",
    "ticket submitted to sit": "indeterminate",
    "to do": "new",
    "to review": "indeterminate",
    "tr review": "indeterminate",
    "under inspection": "indeterminate",
    "under investigation": "indeterminate",
    "under review": "indeterminate",
    "unescalated": "new",
    "unit part approval": "indeterminate",
    "unit part priority assignment": "new",
    "validation": "indeterminate",
    "validation and alarm setup": "indeterminate",
    "vendor": "indeterminate",
    "verification": "indeterminate",
    "verification_failed": "done",
    "voc1": "indeterminate",
    "voc2": "indeterminate",
    "voting": "indeterminate",
    "waiting": "new",
    "waiting for approval": "new",
    "waiting for customer": "new",
    "waiting for merge": "indeterminate",
    "waiting for release": "indeterminate",
    "waiting for review": "new",
    "waiting for sccb": "new",
    "waiting for support": "indeterminate",
    "waiting hq support": "indeterminate",
    "waiting on amhs member": "indeterminate",
    "waiting on customer": "indeterminate",
    "waiting on lot arrival": "indeterminate",
    "waiting on others": "indeterminate",
    "waiting on requestor": "new",
    "waiting on s1": "indeterminate",
    "waiting to be assigned": "new",
    "wc ready": "done",
    "won't do": "done",
    "work in progress": "indeterminate",
    "working": "indeterminate",
  };

  // Get status badge color
  const getStatusColor = (issue: Issue): string => {
    // First try the statusCategoryKey from the API
    const categoryKey = issue.statusCategoryKey?.toLowerCase();
    if (categoryKey && statusCategoryColors[categoryKey]) {
      return statusCategoryColors[categoryKey];
    }

    // Fallback: look up status name in our mapping
    const statusName = issue.status?.toLowerCase() || "";
    const negativeStatusKeywords = ["cancel", "reject", "decline", "won't do"];
    if (negativeStatusKeywords.some((keyword) => statusName.includes(keyword))) {
      return statusCategoryColors["negative"];
    }
    const mappedCategory = statusNameToCategory[statusName];
    if (mappedCategory) {
      return statusCategoryColors[mappedCategory];
    }

    // Default to gray (To Do/New) for unknown statuses
    return statusCategoryColors["new"];
  };

  const priorityColors: Record<string, string> = {
    "High": "bg-destructive/10 text-destructive border-destructive/20",
    "Medium": "bg-chart-5/10 text-chart-5 border-chart-5/20",
    "Low": "bg-muted text-muted-foreground border-border",
  };

  return (
    <div className="w-full space-y-4">
      {/* Header */}
      {(title || subtitle) && (
        <div className="space-y-2">
          {title && (
            <h2 className="text-2xl font-bold text-foreground">{title}</h2>
          )}
          {subtitle && (
            <p className="text-muted-foreground">{subtitle}</p>
          )}
        </div>
      )}

      {/* Error State */}
      {error && (
        <div className="border border-destructive/20 bg-destructive/5 text-destructive rounded-lg p-4">
          <h4 className="font-medium mb-2">Error loading issues</h4>
          <p className="text-sm">
            {error.message.includes('Access denied')
              ? 'You don\'t have permission to view these issues.'
              : error.message
            }
          </p>
          <p className="text-xs mt-2 opacity-75">
            JQL: {jqlQuery}
          </p>
        </div>
      )}

      {/* Loading State */}
      {isLoading && (
        <div className="border border-border rounded-lg overflow-hidden bg-card">
          <div className="p-4">
            <div className="animate-pulse space-y-4">
              <div className="h-4 bg-muted rounded w-1/4"></div>
              <div className="space-y-2">
                {Array.from({ length: 5 }).map((_, i) => (
                  <div key={i} className="h-12 bg-muted rounded"></div>
                ))}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Search and Filter Bar */}
      {(showSearch || showFilter) && !isLoading && !error && (
        <div className="flex gap-4 items-center">
          {showSearch && (
            <div className="flex-1 relative">
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground" />
              <Input
                placeholder="Search requests by key, summary, description..."
                className="pl-10"
                value={searchInput}
                onChange={(e) => setSearchInput(e.target.value)}
              />
              {isSearching && (
                <Loader2 className="absolute right-3 top-1/2 transform -translate-y-1/2 h-4 w-4 text-muted-foreground animate-spin" />
              )}
            </div>
          )}
          {showFilter && (
            <Popover open={isFilterOpen} onOpenChange={setIsFilterOpen}>
              <PopoverTrigger asChild>
                <Button
                  variant={hasActiveFilters ? "default" : "outline"}
                  size="sm"
                >
                  <Filter className="h-4 w-4 mr-2" />
                  Filter
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-64 space-y-3" align="end">
                <div className="space-y-2">
                  <p className="text-xs font-medium text-muted-foreground">Status</p>
                  {availableStatuses.length === 0 ? (
                    <p className="text-xs text-muted-foreground">No status values</p>
                  ) : (
                    availableStatuses.map((status) => (
                      <div key={status} className="flex items-center space-x-2">
                        <Checkbox
                          id={`status-${status}`}
                          checked={statusFilter.includes(status)}
                          onCheckedChange={(checked) =>
                            setStatusFilter((prev) =>
                              checked
                                ? [...prev, status]
                                : prev.filter((s) => s !== status),
                            )
                          }
                        />
                        <Label
                          htmlFor={`status-${status}`}
                          className="text-xs cursor-pointer"
                        >
                          {status}
                        </Label>
                      </div>
                    ))
                  )}
                </div>

                <div className="space-y-2">
                  <p className="text-xs font-medium text-muted-foreground">Priority</p>
                  {availablePriorities.length === 0 ? (
                    <p className="text-xs text-muted-foreground">No priority values</p>
                  ) : (
                    availablePriorities.map((priority) => (
                      <div key={priority} className="flex items-center space-x-2">
                        <Checkbox
                          id={`priority-${priority}`}
                          checked={priorityFilter.includes(priority)}
                          onCheckedChange={(checked) =>
                            setPriorityFilter((prev) =>
                              checked
                                ? [...prev, priority]
                                : prev.filter((p) => p !== priority),
                            )
                          }
                        />
                        <Label
                          htmlFor={`priority-${priority}`}
                          className="text-xs cursor-pointer"
                        >
                          {priority}
                        </Label>
                      </div>
                    ))
                  )}
                </div>

                {hasActiveFilters && (
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="text-xs text-muted-foreground px-0"
                    onClick={() => {
                      setStatusFilter([]);
                      setPriorityFilter([]);
                    }}
                  >
                    Clear filters
                  </Button>
                )}
              </PopoverContent>
            </Popover>
          )}
        </div>
      )}

      {/* Results count */}
      {!isLoading && !error && (
        <div className="text-sm text-muted-foreground flex items-center justify-between">
          <span>
            Showing {sortedIssues.length} {sortedIssues.length === 1 ? "result" : "results"}
            {data?.totalCount && data.totalCount > sortedIssues.length && (
              <span> of {data.totalCount} total</span>
            )}
            {(debouncedSearchTerm || hasActiveFilters) && (
              <span className="ml-1">(filtered)</span>
            )}
          </span>
        </div>
      )}

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
                      className="h-auto p-0 hover:bg-transparent font-semibold"
                      onClick={() => handleSort(column.id)}
                    >
                      {column.name}
                      <ArrowUpDown className="ml-2 h-3 w-3" />
                    </Button>
                  </TableHead>
                ))}
                <TableHead className="w-10"></TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {sortedIssues.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={columns.length + 1} className="text-center py-8 text-muted-foreground">
                    {debouncedSearchTerm || hasActiveFilters ? (
                      <div className="space-y-2">
                        <p>No issues found matching your search/filters</p>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => {
                            setSearchInput("");
                            setStatusFilter([]);
                            setPriorityFilter([]);
                          }}
                          className="text-primary"
                        >
                          Clear search and filters
                        </Button>
                      </div>
                    ) : (
                      <div className="space-y-1">
                        <p>No issues found</p>
                        <p className="text-xs opacity-75">
                          There are no issues matching the current query
                        </p>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ) : (
                sortedIssues.map((issue) => (
                  <TableRow key={issue.key} className="hover:bg-muted transition-colors">
                    {columns.map((column) => (
                      <TableCell key={column.id}>
                        {column.id === "status" ? (
                          <Badge
                            variant="outline"
                            className={cn(
                              "border",
                              getStatusColor(issue)
                            )}
                          >
                            {getIssueFieldValue(issue, column)}
                          </Badge>
                        ) : column.id === "priority" ? (
                          <Badge
                            variant="outline"
                            className={cn(
                              "border",
                              priorityColors[issue.priority] || priorityColors["Medium"]
                            )}
                          >
                            {getIssueFieldValue(issue, column)}
                          </Badge>
                        ) : column.id === "key" ? (
                          (() => {
                            const issueUrl = buildIssueUrl(issue);
                            const keyText = getIssueFieldValue(issue, column);
                            if (!issueUrl) {
                              return (
                                <span className="font-mono text-sm font-medium text-primary">
                                  {keyText}
                                </span>
                              );
                            }
                            return (
                              <a
                                href={issueUrl}
                                target="_blank"
                                rel="noreferrer"
                                className="font-mono text-sm font-medium text-primary rounded-sm px-1 transition-colors hover:bg-muted focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
                                onClick={(e) => e.stopPropagation()}
                              >
                                {keyText}
                              </a>
                            );
                          })()
                        ) : (
                          <span className="text-sm" title={getIssueFieldValue(issue, column)}>
                            {column.id === "summary"
                              ? getIssueFieldValue(issue, column).length > 60
                                ? getIssueFieldValue(issue, column).substring(0, 60) + "..."
                                : getIssueFieldValue(issue, column)
                              : getIssueFieldValue(issue, column)
                            }
                          </span>
                        )}
                      </TableCell>
                    ))}
                    <TableCell>
                      {(() => {
                        const issueUrl = buildIssueUrl(issue);
                        if (!issueUrl) return null;
                        return (
                          <a
                            href={issueUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="inline-flex items-center justify-center h-8 w-8 rounded-md hover:bg-muted"
                            onClick={(e) => e.stopPropagation()}
                          >
                            <ExternalLink className="h-3 w-3" />
                          </a>
                        );
                      })()}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      )}

      {/* Pagination */}
      {!isLoading && !error && data && (
        <div className="flex items-center justify-between text-sm">
          <div className="text-muted-foreground">
            Showing {currentPage * pageSize + 1} to {Math.min((currentPage + 1) * pageSize, data.totalCount)} of {data.totalCount} results
          </div>
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasPreviousPage}
              onClick={() => handlePageChange(currentPage - 1)}
            >
              Previous
            </Button>
            <span className="px-3 py-2 text-xs text-muted-foreground">
              Page {currentPage + 1} of {Math.ceil(data.totalCount / pageSize)}
            </span>
            <Button
              variant="outline"
              size="sm"
              disabled={!data.hasNextPage}
              onClick={() => handlePageChange(currentPage + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      )}
    </div>
  );
}
