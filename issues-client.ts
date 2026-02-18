/* rail-at-sas/frontend/lib/api/issues-client.ts */

/**
 * API Client for Issues
 * Handles all HTTP requests for Jira issue search and JQL operations
 */

const API_BASE = '/rest/rail/1.0';

export interface Issue {
  id: string;
  key: string;
  summary: string;
  description?: string;
  status: string;
  statusId?: string;
  statusIconUrl?: string;
  statusCategoryKey?: string;
  priority: string;
  priorityId?: string;
  priorityIconUrl?: string;
  issueType: string;
  issueTypeId?: string;
  issueTypeIconUrl?: string;
  projectKey?: string;
  projectName?: string;
  created: string;
  updated: string;
  dueDate?: string;
  reporter?: string;
  reporterDisplayName?: string;
  reporterEmailAddress?: string;
  reporterAvatarUrl?: string;
  assignee?: string;
  assigneeDisplayName?: string;
  assigneeEmailAddress?: string;
  assigneeAvatarUrl?: string;
  resolution?: string;
  resolutionId?: string;
  resolutionDate?: string;
  labels?: string[];
  components?: string[];
  fixVersions?: string[];
  affectedVersions?: string[];
  environment?: string;
  timeOriginalEstimate?: number;
  timeEstimate?: number;
  timeSpent?: number;
  votes?: number;
  watcherCount?: number;
  serviceDeskId?: string;
  customFields?: Record<string, string | null>;
}

export interface FieldInfo {
  id: string;
  name: string;
  category: 'system' | 'custom';
  isCustom: boolean;
  customFieldType?: string;
}

export interface ProjectFieldsResponse {
  projectKey: string;
  systemFields: FieldInfo[];
  customFields: FieldInfo[];
  totalFields: number;
}


export interface IssueSearchResponse {
  issues: Issue[];
  totalCount: number;
  startIndex: number;
  pageSize: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  jqlQuery: string;
  projectKey?: string;
  executionTimeMs: number;
  currentPage?: number;
  totalPages?: number;

  searchedAsUserKey?: string | null;
  searchedAsUserName?: string | null;
  searchedAsUserDisplayName?: string | null;
  resolvedJqlQuery?: string | null;

  // NEW: facets for full filter options
  facets?: {
    statuses?: string[];
    priorities?: string[];
  } | null;
}

export interface IssueSearchParams {
  jqlQuery?: string;
  projectKey?: string;
  startIndex?: number;
  pageSize?: number;
  filter?: string;
  includeAllProjectIssues?: boolean;
  searchTerm?: string;
  statusFilter?: string;
  priorityFilter?: string;
}

export async function searchIssues(params: IssueSearchParams): Promise<IssueSearchResponse> {
  const { jqlQuery, startIndex = 0, pageSize = 25, searchTerm, statusFilter, priorityFilter } = params;

  if (!jqlQuery) {
    throw new Error('JQL query is required');
  }

  const searchParams = new URLSearchParams({
    jql: jqlQuery,
    start: startIndex.toString(),
    limit: pageSize.toString(),
  });

  if (searchTerm && searchTerm.trim()) {
    searchParams.set('search', searchTerm.trim());
  }
  if (statusFilter && statusFilter.trim()) {
    searchParams.set('status', statusFilter.trim());
  }
  if (priorityFilter && priorityFilter.trim()) {
    searchParams.set('priority', priorityFilter.trim());
  }

  const response = await fetch(`${API_BASE}/issues/search?${searchParams}`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Failed to search issues: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchProjectIssues(params: IssueSearchParams): Promise<IssueSearchResponse> {
  const { projectKey, startIndex = 0, pageSize = 25 } = params;

  if (!projectKey) {
    throw new Error('Project key is required');
  }

  const searchParams = new URLSearchParams({
    start: startIndex.toString(),
    limit: pageSize.toString(),
  });

  const response = await fetch(`${API_BASE}/projects/${projectKey}/issues?${searchParams}`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Failed to fetch project issues: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchAllProjectIssues(params: IssueSearchParams): Promise<IssueSearchResponse> {
  const { projectKey, startIndex = 0, pageSize = 25 } = params;

  if (!projectKey) {
    throw new Error('Project key is required');
  }

  const searchParams = new URLSearchParams({
    start: startIndex.toString(),
    limit: pageSize.toString(),
  });

  const response = await fetch(`${API_BASE}/projects/${projectKey}/issues/all?${searchParams}`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Failed to fetch all project issues: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchProjectIssuesWithFilter(params: IssueSearchParams): Promise<IssueSearchResponse> {
  const { projectKey, filter, startIndex = 0, pageSize = 25 } = params;

  if (!projectKey) {
    throw new Error('Project key is required');
  }

  const searchParams = new URLSearchParams({
    start: startIndex.toString(),
    limit: pageSize.toString(),
  });

  if (filter) {
    searchParams.set('filter', filter);
  }

  const response = await fetch(`${API_BASE}/projects/${projectKey}/issues/filter?${searchParams}`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Failed to fetch filtered project issues: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchIssues(params: IssueSearchParams): Promise<IssueSearchResponse> {
  if (params.jqlQuery) {
    return searchIssues(params);
  }

  if (params.projectKey) {
    if (params.filter) {
      return fetchProjectIssuesWithFilter(params);
    }

    if (params.includeAllProjectIssues) {
      return fetchAllProjectIssues(params);
    }

    return fetchProjectIssues(params);
  }

  throw new Error('Either jqlQuery or projectKey must be provided');
}

export function buildJQLQuery(options: {
  projectKey?: string;
  reporter?: string | 'currentUser';
  status?: string[];
  priority?: string[];
  assignee?: string | 'currentUser';
  createdAfter?: string;
  createdBefore?: string;
  orderBy?: string;
}): string {
  const clauses: string[] = [];

  if (options.projectKey) {
    clauses.push(`project = ${options.projectKey}`);
  }

  if (options.reporter) {
    if (options.reporter === 'currentUser') {
      clauses.push('reporter = currentUser()');
    } else {
      clauses.push(`reporter = "${options.reporter}"`);
    }
  }

  if (options.assignee) {
    if (options.assignee === 'currentUser') {
      clauses.push('assignee = currentUser()');
    } else {
      clauses.push(`assignee = "${options.assignee}"`);
    }
  }

  if (options.status && options.status.length > 0) {
    if (options.status.length === 1) {
      clauses.push(`status = "${options.status[0]}"`);
    } else {
      const statusList = options.status.map(s => `"${s}"`).join(', ');
      clauses.push(`status IN (${statusList})`);
    }
  }

  if (options.priority && options.priority.length > 0) {
    if (options.priority.length === 1) {
      clauses.push(`priority = "${options.priority[0]}"`);
    } else {
      const priorityList = options.priority.map(p => `"${p}"`).join(', ');
      clauses.push(`priority IN (${priorityList})`);
    }
  }

  if (options.createdAfter) {
    clauses.push(`created >= "${options.createdAfter}"`);
  }

  if (options.createdBefore) {
    clauses.push(`created <= "${options.createdBefore}"`);
  }

  let jql = clauses.join(' AND ');

  if (options.orderBy) {
    jql += ` ORDER BY ${options.orderBy}`;
  } else {
    jql += ' ORDER BY created DESC';
  }

  return jql;
}

export function formatDateForJQL(date: Date): string {
  return date.toISOString().split('T')[0];
}

export async function fetchProjectFields(projectKey: string): Promise<ProjectFieldsResponse> {
  const response = await fetch(`${API_BASE}/projects/${projectKey}/fields`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    throw new Error(errorData.error || `Failed to fetch project fields: ${response.statusText}`);
  }

  return response.json();
}

export async function fetchRecentUserIssues(
  daysBack: number = 60,
  limit: number = 100
): Promise<IssueSearchResponse> {
  const jql = `reporter = currentUser() AND created >= -${daysBack}d ORDER BY created DESC`;
  return searchIssues({
    jqlQuery: jql,
    startIndex: 0,
    pageSize: limit,
  });
}
