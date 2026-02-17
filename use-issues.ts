// rail-at-sas/frontend/hooks/use-issues.ts

import { useQuery } from '@tanstack/react-query';
import {
  searchIssues,
  fetchIssueFacets,
  IssueSearchParams,
  IssueSearchResponse,
  IssueFacetResponse,
} from '@/lib/api/issues-client';

export function useJQLSearch(
  jqlQuery: string | null,
  options?: {
    startIndex?: number;
    pageSize?: number;
    enabled?: boolean;
    searchTerm?: string;
    statusFilter?: string;
    priorityFilter?: string;
  }
) {
  const {
    startIndex = 0,
    pageSize = 25,
    enabled = true,
    searchTerm,
    statusFilter,
    priorityFilter,
  } = options || {};

  return useQuery<IssueSearchResponse>({
    queryKey: [
      'issues',
      jqlQuery,
      startIndex,
      pageSize,
      searchTerm,
      statusFilter,
      priorityFilter,
    ],
    queryFn: () =>
      searchIssues({
        jqlQuery: jqlQuery || undefined,
        startIndex,
        pageSize,
        searchTerm,
        statusFilter,
        priorityFilter,
      }),
    enabled: enabled && !!jqlQuery,
    staleTime: 2 * 60 * 1000,
  });
}

/**
 * NEW: Hook for fetching ALL possible statuses + priorities
 */
export function useIssueFacets(jqlQuery: string | null) {
  return useQuery<IssueFacetResponse>({
    queryKey: ['issue-facets', jqlQuery],
    queryFn: () => fetchIssueFacets(jqlQuery!),
    enabled: !!jqlQuery,
    staleTime: 5 * 60 * 1000,
  });
}
