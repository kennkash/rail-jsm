// /rail-at-sas/frontend/hooks/use-request-types.ts
import { useQuery } from '@tanstack/react-query';
import { fetchRequestTypes, fetchRequestTypeById, searchRequestTypes, searchAllRequestTypes } from '@/lib/api/request-types-client';

export function useRequestTypes(projectKey: string | null) {
  return useQuery({
    queryKey: ['request-types', projectKey],
    queryFn: () => fetchRequestTypes(projectKey!),
    enabled: !!projectKey,
    staleTime: 5 * 60 * 1000,
    retry: 2,
  });
}

export function useRequestType(requestTypeId: string | null) {
  return useQuery({
    queryKey: ['request-type', requestTypeId],
    queryFn: () => fetchRequestTypeById(requestTypeId!),
    enabled: !!requestTypeId,
    staleTime: 10 * 60 * 1000,
  });
}

export function useSearchRequestTypes(projectKey: string | null, searchTerm: string) {
  return useQuery({
    queryKey: ['request-types-search', projectKey, searchTerm],
    queryFn: () => searchRequestTypes(projectKey!, searchTerm),
    enabled: !!projectKey && searchTerm.length > 0,
    staleTime: 2 * 60 * 1000,
  });
}

/**
 * Hook for global request type search across all service desk projects
 */
export function useGlobalRequestTypeSearch(searchTerm: string, limit: number = 20) {
  return useQuery({
    queryKey: ['request-types-global-search', searchTerm, limit],
    queryFn: () => searchAllRequestTypes(searchTerm, limit),
    enabled: searchTerm.length >= 2, // Only search with 2+ characters
    staleTime: 2 * 60 * 1000,
  });
}
