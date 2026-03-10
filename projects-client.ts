/**
 * API Client for Projects
 * Handles all HTTP requests for Jira Service Desk projects
 */

const API_BASE = '/rest/rail/1.0';

export interface Project {
  id: string;
  key: string;
  name: string;
  description?: string;
  avatarUrl?: string;
  projectTypeKey?: string;
  serviceDeskId?: string;
  portalId?: string;
  portalName?: string;
  requestTypeCount?: number;
  isServiceDesk: boolean;
}

export interface ProjectsResponse {
  projects: Project[];
  count: number;
}

// /rail-at-sas/frontend/lib/api/projects-client.ts
/**
 * Fetch all service desk projects
 */
export async function fetchProjects(): Promise<ProjectsResponse> {
  const response = await fetch(`${API_BASE}/projects`);

  if (!response.ok) {
    throw new Error(`Failed to fetch projects: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Fetch a single project by key
 */
export async function fetchProjectByKey(projectKey: string): Promise<Project> {
  const response = await fetch(`${API_BASE}/projects/${projectKey}`);

  if (!response.ok) {
    throw new Error(`Failed to fetch project: ${response.statusText}`);
  }

  return response.json();
}

/**
 * Search projects by name or key
 */
export async function searchProjects(
  searchTerm: string
): Promise<{ projects: Project[]; count: number; searchTerm: string }> {
  const params = new URLSearchParams({ q: searchTerm });
  const response = await fetch(`${API_BASE}/projects/search?${params}`);

  if (!response.ok) {
    throw new Error(`Failed to search projects: ${response.statusText}`);
  }

  return response.json();
}
