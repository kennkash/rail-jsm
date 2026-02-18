// rail-at-sas/frontend/components/portal-builder/form-components/portal-jql-table.tsx
import { FormComponentModel } from "@/models/FormComponent";
import { UseFormReturn, ControllerRenderProps, FieldValues } from "react-hook-form";
import { ReactCode, Viewports } from "@/types/portal-builder.types";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Popover, PopoverTrigger, PopoverContent } from "@/components/ui/popover";
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from "@/components/ui/command";
import { ArrowUpDown, Search, Filter, ExternalLink, Loader2, GripVertical, X, Plus } from "lucide-react";
import { cn } from "@/lib/utils";
import { usePortalBuilderStore } from "@/stores/portal-builder-store";
import { useShallow } from "zustand/react/shallow";
import { useState, useMemo, useEffect } from "react";
import { useJQLSearch, useProjectIssues } from "@/hooks/use-issues";
import { Issue, FieldInfo, fetchProjectFields } from "@/lib/api/issues-client";
import { useQuery } from "@tanstack/react-query";
import {
  DndContext,
  closestCenter,
  KeyboardSensor,
  PointerSensor,
  useSensor,
  useSensors,
  DragEndEvent,
} from "@dnd-kit/core";
import {
  arrayMove,
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from "@dnd-kit/sortable";
import { CSS } from "@dnd-kit/utilities";

/**
 * Column configuration for JQL Table
 * Supports both legacy string format and new object format
 */
interface ColumnConfig {
  id: string;
  name: string;
  isCustom: boolean;
}

// Legacy type for backward compatibility
type LegacyIssueColumn = "key" | "summary" | "status" | "priority" | "created" | "reporter" | "assignee" | "issueType" | "updated" | "dueDate" | "resolution" | "labels" | "components" | "fixVersions" | "affectedVersions";

/**
 * Available system columns for JQL Table (used for legacy migration and defaults)
 */
const systemColumns: Array<{ id: LegacyIssueColumn; name: string }> = [
  { id: "key", name: "Key" },
  { id: "summary", name: "Summary" },
  { id: "status", name: "Status" },
  { id: "priority", name: "Priority" },
  { id: "issueType", name: "Issue Type" },
  { id: "created", name: "Created" },
  { id: "updated", name: "Updated" },
  { id: "dueDate", name: "Due Date" },
  { id: "reporter", name: "Reporter" },
  { id: "assignee", name: "Assignee" },
  { id: "resolution", name: "Resolution" },
  { id: "labels", name: "Labels" },
  { id: "components", name: "Components" },
  { id: "fixVersions", name: "Fix Versions" },
  { id: "affectedVersions", name: "Affected Versions" },
];

const defaultJqlColumns: ColumnConfig[] = [
  { id: "key", name: "Key", isCustom: false },
  { id: "summary", name: "Summary", isCustom: false },
  { id: "status", name: "Status", isCustom: false },
  { id: "priority", name: "Priority", isCustom: false },
  { id: "created", name: "Created", isCustom: false },
];

const MAX_COLUMNS = 8;

/**
 * Migrate legacy column format (string[]) to new format (ColumnConfig[])
 */
function migrateColumns(columns: unknown): ColumnConfig[] {
  if (!columns) return defaultJqlColumns;

  // Already in new format
  if (Array.isArray(columns) && columns.length > 0 && typeof columns[0] === 'object' && 'id' in columns[0]) {
    return columns as ColumnConfig[];
  }

  // Legacy string array format
  if (Array.isArray(columns) && columns.length > 0 && typeof columns[0] === 'string') {
    return (columns as string[]).map(id => {
      const systemCol = systemColumns.find(c => c.id === id);
      return {
        id,
        name: systemCol?.name || id,
        isCustom: !systemCol,
      };
    });
  }

  return defaultJqlColumns;
}

/**
 * PortalJQLTable Widget
 * Displays Jira issues using JQL query with search, filter, and sort
 *
 * Features:
 * - Configurable JQL query
 * - Search functionality
 * - Column sorting
 * - Status/Priority badges with CSS variables
 * - Pagination support
 *
 * Widget Rules:
 * - Reusable (can have multiple on a portal)
 * - Full-width widget
 * - Uses sample data until Jira endpoint connected
 */
export function PortalJQLTable(
  component: FormComponentModel,
  _form: UseFormReturn<FieldValues, undefined>,
  _field: ControllerRenderProps,
  viewport?: Viewports
) {
  const title = component.getField("content", viewport) || "Your Requests";
  const subtitle = component.getField("description", viewport) || "";
  const jqlQuery = component.getField("properties.jqlQuery", viewport) || "project = {{PROJECT_KEY}} AND reporter = currentUser()";
  const rawColumns = component.getField("properties.columns", viewport);
  const columns = migrateColumns(rawColumns);
  const showSearch = component.getField("properties.showSearch", viewport) !== "no";
  const showFilter = component.getField("properties.showFilter", viewport) !== "no";
  const useCustomerPortalLinks = component.getField("properties.useCustomerPortalLinks", viewport) === "yes";
  const pageSize = parseInt(component.getField("properties.pageSize", viewport) || "10");

  // Get project and service desk context from the portal builder store
  const { projectKey, serviceDeskId, isServiceDesk } = usePortalBuilderStore(
    useShallow((state) => ({
      projectKey: state.projectKey,
      serviceDeskId: state.serviceDeskId,
      isServiceDesk: state.isServiceDesk,
    })),
  );
  const configuredProjectKey = (component.getField("properties.projectKey", viewport) as string | undefined)?.trim();
  const resolvedApiProjectKey = configuredProjectKey || projectKey || undefined;
  const effectiveProjectKey = (resolvedApiProjectKey || "PROJECT").toUpperCase();

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

  // Determine whether to use JQL or project-based query
  const resolvedJqlQuery = useMemo(() => {
    if (!jqlQuery || typeof jqlQuery !== "string") {
      return null;
    }

    const placeholderPatterns = [
      /\{\{\s*PROJECT_KEY\s*\}\}/gi,
      /\{\{\s*PROJECT\s*\}\}/gi,
      /\$\{\s*projectKey\s*\}/gi,
    ];

    let normalized = jqlQuery;
    placeholderPatterns.forEach((pattern) => {
      normalized = normalized.replace(pattern, effectiveProjectKey);
    });

    const missingProjectRegex = /project\s*=\s*(?=(?:AND|OR|\)|$))/gi;
    if (missingProjectRegex.test(normalized)) {
      normalized = normalized.replace(
        missingProjectRegex,
        `project = ${effectiveProjectKey} `,
      );
    }

    const trimmed = normalized.trim();
    return trimmed.length > 0 ? trimmed : null;
  }, [jqlQuery, effectiveProjectKey]);

  const shouldUseJQL = Boolean(resolvedJqlQuery);
  const fallbackProjectKey = resolvedApiProjectKey || "DEMO"; // Fallback for development

  // Build server-side filter params (comma-separated strings)
  const serverStatusFilter = statusFilter.length > 0 ? statusFilter.join(",") : undefined;
  const serverPriorityFilter = priorityFilter.length > 0 ? priorityFilter.join(",") : undefined;

  // Use JQL search if query contains project, otherwise use project-based search
  // SERVER-SIDE SEARCH/FILTER: Pass search and filter params to the API
  // This enables searching across the ENTIRE result set, not just the current page
  const { data: jqlData, isLoading: isJqlLoading, error: jqlError, isFetching: isJqlFetching } = useJQLSearch(
    shouldUseJQL ? resolvedJqlQuery : null,
    {
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
      // Include facets so filter dropdowns can show ALL available values
      includeFacets: shouldUseJQL && showFilter,
    }
  );

  const { data: projectData, isLoading: isProjectLoading, error: projectError } = useProjectIssues(
    !shouldUseJQL ? fallbackProjectKey : null,
    {
      startIndex: currentPage * pageSize,
      pageSize,
      enabled: !shouldUseJQL,
    }
  );

  // Show loading indicator when fetching (includes refetches for search/filter)
  const isSearching = isJqlFetching && debouncedSearchTerm !== "";

  // Use the appropriate data source
  const data = shouldUseJQL ? jqlData : projectData;
  const isLoading = shouldUseJQL ? isJqlLoading : isProjectLoading;
  const error = shouldUseJQL ? jqlError : projectError;
  const issues = data?.issues || [];

  const availableStatuses = useMemo(
    () => {
      // Prefer server facets (full JQL match-set), fallback to current page values
      const facetStatuses = (data as any)?.facets?.statuses as string[] | undefined;
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
    },
    [data, issues],
  );

  const availablePriorities = useMemo(
    () => {
      // Prefer server facets (full JQL match-set), fallback to current page values
      const facetPriorities = (data as any)?.facets?.priorities as string[] | undefined;
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
    },
    [data, issues],
  );

  const hasActiveFilters = statusFilter.length > 0 || priorityFilter.length > 0;

  const handleSort = (columnId: string) => {
    if (sortColumn === columnId) {
      setSortDirection(sortDirection === "asc" ? "desc" : "asc");
      // Sorting changes affect ordering across entire set; reset to first page
      setCurrentPage(0);
    } else {
      setSortColumn(columnId);
      setSortDirection("asc");
      // Sorting changes affect ordering across entire set; reset to first page
      setCurrentPage(0);
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

  // Get sortable value for issue field - MUST be defined before useMemo that uses it
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
        // Custom field or unknown field
        if (columnId.startsWith("customfield_") && issue.customFields) {
          return issue.customFields[columnId] || undefined;
        }
        return undefined;
    }
  };

  // Get display value for issue field - MUST be defined before useMemo that uses it
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

  /**
  * Display issues:
  * - In JQL mode: server sorts the ENTIRE result set via sortField/sortDir, so do NOT sort client-side.
  * - In non-JQL (project) mode: keep client-side sorting (because that endpoint doesn't support sort params).
  */
  const displayIssues = useMemo(() => {
    if (shouldUseJQL) {
      return issues;
    }
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
  }, [issues, shouldUseJQL, sortColumn, sortDirection]);

  const buildIssueUrl = (issue: Issue): string | null => {
    if (!issue?.key) return null;

    // Per-issue descision: only use portal link when THIS issue has a serviceDeskId
    const issueServiceDeskId = issue.serviceDeskId;

    if (useCustomerPortalLinks && issueServiceDeskId) {
      return `/servicedesk/customer/portal/${issueServiceDeskId}/${issue.key}`;
    }
    return `/browse/${issue.key}`;
  };

  // Status category colors based on Jira Data Center standard categories
  // 'new' = To Do (gray), 'indeterminate' = In Progress (blue), 'done' = Done (green)
  const statusCategoryColors: Record<string, string> = {
    "new": "bg-muted text-muted-foreground border-border", // To Do - Gray
    "indeterminate": "bg-blue-100 text-blue-700 border-blue-200 dark:bg-blue-900/30 dark:text-blue-400 dark:border-blue-800", // In Progress - Blue
    "done": "bg-emerald-100 text-emerald-700 border-emerald-200 dark:bg-emerald-900/30 dark:text-emerald-400 dark:border-emerald-800", // Done - Green
    "negative": "bg-destructive/10 text-destructive border-destructive/20 dark:bg-destructive/30 dark:text-destructive/70 dark:border-destructive/80", // Negative statuses - Red
  };

  // Comprehensive status name to category mapping derived from scsirpeoit.txt
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

  // Get status badge color based on status category key or status name fallback
  const getStatusColor = (issue: Issue): string => {
    // First try the statusCategoryKey from the API
    const categoryKey = issue.statusCategoryKey?.toLowerCase();
    if (categoryKey && statusCategoryColors[categoryKey]) {
      return statusCategoryColors[categoryKey];
    }

    // Fallback: look up status name in our mapping
    const statusName = issue.status?.toLowerCase() || "";
    // Negative keywords include cancel/reject keywords before falling back to the larger catalog
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
              ? 'You don\'t have permission to view issues in this project.'
              : error.message
            }
          </p>
          <p className="text-xs mt-2 opacity-75">
            JQL: {resolvedJqlQuery || jqlQuery || "N/A"}
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
      {(showSearch || showFilter) && (
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
              {/* Show loading indicator when search is being processed */}
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
            Showing {displayIssues.length} {displayIssues.length === 1 ? "result" : "results"}
            {data?.totalCount && data.totalCount > displayIssues.length && (
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
              {displayIssues.length === 0 ? (
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
