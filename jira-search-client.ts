// /rail-at-sas/frontend/lib/api/jira-search-client.ts

/**
 * API Client for Jira Service Desk Native Search Endpoints
 * Calls the out-of-the-box JSM endpoints for KB articles and request type search
 */

type BootstrapWindow = typeof window & {
  RAIL_PORTAL_BOOTSTRAP?: {
    baseUrl?: string;
  };
};

function getBootstrapBaseUrl(): string {
  if (typeof window === "undefined") {
    return "";
  }
  const bootstrap = (window as BootstrapWindow).RAIL_PORTAL_BOOTSTRAP;
  return (bootstrap?.baseUrl ?? window.location.origin).replace(/\/$/, "");
}


/**
 * Convert Jira secure avatar URLs to the customer-shim version
 * so unlicensed portal users can load request type icons.
 */
function toCustomerShimAvatarUrl(rawUrl: string, baseUrl: string): string {
  try {
    const absolute =
      rawUrl.startsWith("http://") || rawUrl.startsWith("https://")
        ? new URL(rawUrl)
        : new URL(rawUrl, baseUrl || window.location.origin);

    const avatarId = absolute.searchParams.get("avatarId");
    const avatarType = absolute.searchParams.get("avatarType") || "SD_REQTYPE";
    const size = absolute.searchParams.get("size");

    if (!avatarId) return rawUrl;

    const rewritten = new URL(baseUrl || absolute.origin);
    rewritten.pathname = "/servicedesk/customershim/secure/viewavatar";
    rewritten.searchParams.set("avatarType", avatarType);
    rewritten.searchParams.set("avatarId", avatarId);
    if (size) {
      rewritten.searchParams.set("size", size);
    }

    return rewritten.toString();
  } catch {
    return rawUrl;
  }
}


/**
 * Knowledge Base Article from JSM search
 */
export interface KBArticle {
  id: string;
  title: string;
  excerpt?: string;
  url?: string;
  source?: {
    type: string;
    pageId?: string;
    spaceKey?: string;
  };
}

/**
 * Request Type from JSM search (using RAIL API which wraps native Jira)
 */
export interface JiraRequestType {
  id: string;
  name: string;
  description?: string;
  helpText?: string;
  icon?: string;
  iconUrl?: string;
  color?: string;
  group?: string;
  groupIds?: string[];
  serviceDeskId?: string;
  portalId?: string;
  projectKey?: string;
}

export interface KBArticlesResponse {
  articles: KBArticle[];
  total: number;
}

// Hardcoded Confluence base URL per requirements (fallback)
const CONFLUENCE_BASE_URL = "https://confluence.samsungaustin.com";

/**
 * Clean KB article text by removing JSM highlight markers
 * The API uses @@@hl@@@ and @@@endhl@@@ to mark search term highlights
 */
function cleanHighlightMarkers(text: string): string {
  if (!text) return "";
  return text
    .replace(/@@@hl@@@/g, "")      // Remove highlight start markers
    .replace(/@@@endhl@@@/g, "")   // Remove highlight end markers
    .replace(/&hellip;/g, "...")   // Replace HTML ellipsis entity
    .replace(/\s+/g, " ")          // Normalize multiple spaces to single space
    .trim();
}

/**
 * Construct the full Confluence URL from API response
 * Uses appLinkUrl (base) + url (relative path) from the response
 */
function constructConfluenceUrl(appLinkUrl?: string, relativePath?: string): string | null {
  if (!relativePath) return null;
  // Use appLinkUrl from response if available, otherwise fallback to hardcoded
  const baseUrl = appLinkUrl || CONFLUENCE_BASE_URL;
  // Ensure no double slashes when combining
  const cleanBase = baseUrl.replace(/\/$/, "");
  const cleanPath = relativePath.startsWith("/") ? relativePath : `/${relativePath}`;
  return `${cleanBase}${cleanPath}`;
}

export interface RequestTypeSearchResponse {
  results: JiraRequestType[];
  count: number;
  searchTerm: string;
}

/**
 * Search knowledge base articles using native JSM endpoint
 * GET /rest/servicedesk/knowledgebase/latest/articles/search
 *
 * @param projectKey - The project key (e.g., "WMPR")
 * @param query - Search query string
 * @param resultsPerPage - Number of results per page (default 3)
 * @returns Articles or empty array on error (no error message shown to user)
 */
export async function searchKBArticles(
  projectKey: string,
  query: string,
  resultsPerPage: number = 3
): Promise<KBArticlesResponse> {
  const baseUrl = getBootstrapBaseUrl() || "";
  // Don't double-encode - URLSearchParams handles encoding
  const params = new URLSearchParams({
    project: projectKey,
    resultsPerPage: String(resultsPerPage),
    pageNumber: "1",
    query: query,
    _: String(Date.now()),
  });

  try {
    const response = await fetch(
      `${baseUrl}/rest/servicedesk/knowledgebase/latest/articles/search?${params}`,
      { credentials: "same-origin" }
    );

    if (!response.ok) {
      return { articles: [], total: 0 };
    }

    const data = await response.json();
    console.log("KB search raw response:", data);

    // The JSM KB search response uses "results" array
    const rawArticles = data.results || data.articles || data.values || [];

    const articles: KBArticle[] = rawArticles.map((article: any) => {
      // Extract the raw excerpt from bodyTextHighlights (used for excerpts in this API)
      const rawExcerpt = article.bodyTextHighlights || article.excerpt || article.summary || "";
      // Construct full URL from appLinkUrl (base) + url (relative path)
      const fullUrl = constructConfluenceUrl(article.appLinkUrl, article.url);

      return {
        id: article.id || String(Math.random()),
        title: cleanHighlightMarkers(article.title || "Untitled"),
        excerpt: cleanHighlightMarkers(rawExcerpt),
        url: fullUrl,
        source: {
          type: "confluence",
          pageId: article.id,
          spaceKey: article.spaceKey,
        },
      };
    });

    console.log("KB parsed articles:", articles);

    return {
      articles,
      total: data.total || data.size || articles.length,
    };
  } catch (error) {
    return { articles: [], total: 0 };
  }
}

/**
 * Search request types using RAIL API endpoint
 * This uses the existing proven RAIL endpoint that wraps native Jira APIs
 * GET /rest/rail/1.0/projects/{projectKey}/request-types/search?q={query}
 *
 * @param projectKey - The project key
 * @param query - Search query string
 * @returns Request types matching query
 */
export async function searchJiraRequestTypes(
  projectKey: string,
  query: string
): Promise<RequestTypeSearchResponse> {
  const baseUrl = getBootstrapBaseUrl() || "";
  const params = new URLSearchParams({
    q: query,
  });

  try {
    const response = await fetch(
      `${baseUrl}/rest/rail/1.0/projects/${encodeURIComponent(projectKey)}/request-types/search?${params}`,
      { credentials: "same-origin" }
    );

    if (!response.ok) {
      return { results: [], count: 0, searchTerm: query };
    }

    const data = await response.json();

    return {
      results: data.results || [],
      count: data.count || 0,
      searchTerm: data.searchTerm || query,
    };
  } catch (error) {
    return { results: [], count: 0, searchTerm: query };
  }
}

/**
 * Fetch request type icons from native JSM API
 * GET /rest/servicedeskapi/servicedesk/{serviceDeskId}/requesttype
 *
 * This returns a mapping of request type ID to icon URL
 * Used to enrich search results with icons since RAIL API doesn't return them
 */
export async function fetchJsmRequestTypeIcons(
  serviceDeskId: string
): Promise<Record<string, string>> {
  const baseUrl = getBootstrapBaseUrl() || "";

  try {
    const response = await fetch(
      `${baseUrl}/rest/servicedeskapi/servicedesk/${serviceDeskId}/requesttype?limit=100`,
      { credentials: "same-origin" }
    );

    if (!response.ok) {
      return {};
    }

    const payload = await response.json();
    const values = Array.isArray(payload?.values) ? payload.values : [];

    const mapping: Record<string, string> = {};

    for (const value of values) {
      if (!value || !value.id) continue;
      const iconUrls = value.icon?._links?.iconUrls as Record<string, string> | undefined;
      if (!iconUrls) continue;

      const url =
        iconUrls["32x32"] ||
        iconUrls["24x24"] ||
        iconUrls["16x16"] ||
        iconUrls["48x48"];

      if (typeof url === "string" && url.length > 0) {
        mapping[String(value.id)] = toCustomerShimAvatarUrl(url, baseUrl);
      }
    }

    return mapping;
  } catch (error) {
    return {};
  }
}

