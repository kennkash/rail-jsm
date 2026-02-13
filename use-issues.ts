/* /mnt/k.kashmiry/git/rail-at-sas/frontend/hooks/use-issues.ts  */

import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchIssues, IssueSearchParams, IssueSearchResponse } from '@/lib/api/issues-client';

/**
 * Hook for searching issues with JQL or project-based queries
 */
export function useIssues(params: IssueSearchParams, enabled: boolean = true) {
  return useQuery<IssueSearchResponse, Error>({
    queryKey: ['issues', params],
    queryFn: () => fetchIssues(params),
    enabled: enabled && (!!params.jqlQuery || !!params.projectKey),
    staleTime: 2 * 60 * 1000, // 2 minutes
    retry: (failureCount, error) => {
      // Don't retry on permission errors
      if (error.message.includes('Access denied') || error.message.includes('Forbidden')) {
        return false;
      }
      return failureCount < 2;
    },
    retryDelay: (attemptIndex) => Math.min(1000 * 2 ** attemptIndex, 10000),
  });
}

/**
 * Hook for searching issues with JQL query.
 * Supports server-side search and filtering across the ENTIRE result set.
 */
export function useJQLSearch(jqlQuery: string | null, options?: {
  startIndex?: number;
  pageSize?: number;
  enabled?: boolean;
  /** Server-side text search (searches key, summary, description) */
  searchTerm?: string;
  /** Server-side status filter (comma-separated status names) */
  statusFilter?: string;
  /** Server-side priority filter (comma-separated priority names) */
  priorityFilter?: string;
}) {
  const {
    startIndex = 0,
    pageSize = 25,
    enabled = true,
    searchTerm,
    statusFilter,
    priorityFilter,
  } = options || {};

  return useIssues(
    {
      jqlQuery: jqlQuery || undefined,
      startIndex,
      pageSize,
      searchTerm,
      statusFilter,
      priorityFilter,
    },
    enabled && !!jqlQuery
  );
}

/**
 * Hook for getting project issues (current user's issues by default)
 */
export function useProjectIssues(projectKey: string | null, options?: {
  startIndex?: number;
  pageSize?: number;
  filter?: string;
  includeAllProjectIssues?: boolean;
  enabled?: boolean;
}) {
  const { 
    startIndex = 0, 
    pageSize = 25, 
    filter, 
    includeAllProjectIssues = false, 
    enabled = true 
  } = options || {};
  
  return useIssues(
    {
      projectKey: projectKey || undefined,
      startIndex,
      pageSize,
      filter,
      includeAllProjectIssues,
    },
    enabled && !!projectKey
  );
}

/**
 * Hook to prefetch next page of issues
 */
export function usePrefetchIssuesNextPage() {
  const queryClient = useQueryClient();
  
  return (params: IssueSearchParams) => {
    const nextPageParams = {
      ...params,
      startIndex: (params.startIndex || 0) + (params.pageSize || 25),
    };
    
    queryClient.prefetchQuery({
      queryKey: ['issues', nextPageParams],
      queryFn: () => fetchIssues(nextPageParams),
      staleTime: 2 * 60 * 1000,
    });
  };
}

/**
 * Hook to invalidate issues queries (useful after creating/updating issues)
 */
export function useInvalidateIssues() {
  const queryClient = useQueryClient();
  
  return {
    invalidateAll: () => queryClient.invalidateQueries({ queryKey: ['issues'] }),
    invalidateProject: (projectKey: string) => 
      queryClient.invalidateQueries({ 
        queryKey: ['issues'], 
        predicate: (query) => {
          const params = query.queryKey[1] as IssueSearchParams;
          return params?.projectKey === projectKey;
        }
      }),
    invalidateJQL: (jqlQuery: string) => 
      queryClient.invalidateQueries({ 
        queryKey: ['issues'], 
        predicate: (query) => {
          const params = query.queryKey[1] as IssueSearchParams;
          return params?.jqlQuery === jqlQuery;
        }
      }),
  };
}
