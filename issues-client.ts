// rail-at-sas/frontend/lib/api/issues-client.ts

const API_BASE = '/rest/rail/1.0';

export interface IssueFacetResponse {
  statuses: string[];
  priorities: string[];
}

export interface IssueSearchParams {
  jqlQuery?: string;
  projectKey?: string;
  startIndex?: number;
  pageSize?: number;
  searchTerm?: string;
  statusFilter?: string;
  priorityFilter?: string;
}

export interface IssueSearchResponse {
  issues: Issue[];
  totalCount: number;
  startIndex: number;
  pageSize: number;
  hasNextPage: boolean;
  hasPreviousPage: boolean;
  jqlQuery: string;
  executionTimeMs: number;
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

  if (searchTerm) searchParams.set('search', searchTerm);
  if (statusFilter) searchParams.set('status', statusFilter);
  if (priorityFilter) searchParams.set('priority', priorityFilter);

  const response = await fetch(`${API_BASE}/issues/search?${searchParams}`, {
    credentials: 'same-origin',
  });

  if (!response.ok) {
    throw new Error(`Failed to search issues`);
  }

  return response.json();
}

/**
 * NEW: Fetch ALL statuses + priorities for full JQL result
 */
export async function fetchIssueFacets(jqlQuery: string): Promise<IssueFacetResponse> {
  const response = await fetch(
    `${API_BASE}/issues/facets?jql=${encodeURIComponent(jqlQuery)}`,
    { credentials: 'same-origin' }
  );

  if (!response.ok) {
    throw new Error('Failed to fetch issue facets');
  }

  return response.json();
}
