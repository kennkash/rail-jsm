// /rail-at-sas/frontend/components/landing/landing-hero-banner.tsx
"use client";

import { useState, useEffect, useMemo, type ReactNode } from "react";
import { useQueries } from "@tanstack/react-query";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Search, Loader2, FileText, ArrowRight, FolderTree } from "lucide-react";
import { Charset, Index } from "flexsearch";
import { fetchJsmRequestTypeIcons } from "@/lib/api/jira-search-client";
import { fetchProjects, type Project } from "@/lib/api/projects-client";
import { buildRequestTypeUrl } from "@/types/router";
import {
  searchAllRequestTypes,
  type GlobalRequestTypeSearchResult,
} from "@/lib/api/request-types-client";
import type { PortalInfo } from "@/lib/api/portals-client";

// Static configuration - hard-coded values
const HERO_TITLE = "Samsung Customer Request Portal";
const HERO_SUBTITLE = "Search for the right portal, then submit your request";
const FIXED_MIN_HEIGHT = "min-h-[18rem]";
const HERO_BACKGROUND_IMAGE = "https://jira.samsungaustin.com/secure/attachment/503395/503395_sas_building2-resized.jpg";
const SEARCH_LIMIT = 20;
const FUZZY_SEARCH_LIMIT = 30;

type LandingHeroBannerProps = {
  visiblePortals: PortalInfo[];
  onPortalSelect: (portal: PortalInfo) => void;
};

type PortalSearchDoc = {
  id: string;
  type: "portal";
  portal: PortalInfo;
  projectName: string;
  projectKey: string;
  description: string;
  normalizedProjectName: string;
  normalizedProjectKey: string;
  normalizedDescription: string;
};

type RequestTypeSearchDoc = {
  id: string;
  type: "requestType";
  result: GlobalRequestTypeSearchResult;
  normalizedRequestTypeName: string;
  normalizedProjectName: string;
  normalizedProjectKey: string;
};

type SearchResultItem =
  | {
      id: string;
      type: "portal";
      score: number;
      matchedOn: "projectName" | "projectKey" | "description";
      portal: PortalInfo;
      projectName: string;
      projectKey: string;
      description: string;
    }
  | {
      id: string;
      type: "requestType";
      score: number;
      matchedOn: "requestTypeName" | "projectName" | "projectKey";
      result: GlobalRequestTypeSearchResult;
    };

function normalizeSearchText(value?: string): string {
  if (!value) return "";

  return value
    .toLowerCase()
    .replace(/#/g, " ")
    .replace(/[^\p{L}\p{N}\s]+/gu, " ")
    .replace(/\s+/g, " ")
    .trim();
}

function tokenizeQuery(query: string): string[] {
  return normalizeSearchText(query)
    .split(" ")
    .map((token) => token.trim())
    .filter(Boolean);
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function getTextPartsWithHighlights(text: string, query: string): Array<{ text: string; highlight: boolean }> {
  if (!text?.trim()) {
    return [{ text, highlight: false }];
  }

  const tokens = tokenizeQuery(query).filter((token) => token.length > 0);
  if (!tokens.length) {
    return [{ text, highlight: false }];
  }

  const regex = new RegExp(`(${tokens.map(escapeRegExp).join("|")})`, "ig");
  const parts: Array<{ text: string; highlight: boolean }> = [];
  let lastIndex = 0;

  for (const match of text.matchAll(regex)) {
    const index = match.index ?? 0;
    const matchedText = match[0];

    if (index > lastIndex) {
      parts.push({
        text: text.slice(lastIndex, index),
        highlight: false,
      });
    }

    parts.push({
      text: matchedText,
      highlight: true,
    });

    lastIndex = index + matchedText.length;
  }

  if (lastIndex < text.length) {
    parts.push({
      text: text.slice(lastIndex),
      highlight: false,
    });
  }

  return parts.length ? parts : [{ text, highlight: false }];
}

function highlightText(text: string, query: string): ReactNode {
  const parts = getTextPartsWithHighlights(text, query);

  return (
    <>
      {parts.map((part, index) =>
        part.highlight ? (
          <mark key={`${part.text}-${index}`} className="rounded bg-yellow-200/70 px-0.5 text-inherit">
            {part.text}
          </mark>
        ) : (
          <span key={`${part.text}-${index}`}>{part.text}</span>
        )
      )}
    </>
  );
}

function getHighlightedSnippet(text: string, query: string, radius = 55): ReactNode {
  if (!text?.trim()) return text;

  const tokens = tokenizeQuery(query);
  if (!tokens.length) return text;

  const normalizedText = normalizeSearchText(text);

  let firstMatchIndex = -1;
  for (const token of tokens) {
    const idx = normalizedText.indexOf(token);
    if (idx !== -1 && (firstMatchIndex === -1 || idx < firstMatchIndex)) {
      firstMatchIndex = idx;
    }
  }

  if (firstMatchIndex === -1) {
    return text;
  }

  const searchRegex = new RegExp(tokens.map(escapeRegExp).join("|"), "i");
  const visibleMatch = text.match(searchRegex);

  if (!visibleMatch || visibleMatch.index == null) {
    return highlightText(text, query);
  }

  const matchStart = visibleMatch.index;
  const matchEnd = matchStart + visibleMatch[0].length;

  const start = Math.max(0, matchStart - radius);
  const end = Math.min(text.length, matchEnd + radius);

  const prefix = start > 0 ? "…" : "";
  const suffix = end < text.length ? "…" : "";
  const visible = text.slice(start, end);

  return (
    <>
      {prefix}
      {highlightText(visible, query)}
      {suffix}
    </>
  );
}

function matchesAllTokens(fields: string[], query: string): boolean {
  const tokens = tokenizeQuery(query);
  if (!tokens.length) return false;

  return tokens.every((token) => fields.some((field) => field.includes(token)));
}

function getFieldScore(
  query: string,
  value: string,
  baseScore: number,
  exactBonus: number,
  startsWithBonus: number,
  includesBonus: number
): number {
  if (!query || !value) return 0;
  if (value === query) return baseScore + exactBonus;
  if (value.startsWith(query)) return baseScore + startsWithBonus;
  if (value.includes(query)) return baseScore + includesBonus;
  return 0;
}

function getTokenAwareFieldScore(
  tokens: string[],
  value: string,
  baseScore: number,
  exactBonus: number,
  startsWithBonus: number,
  includesBonus: number
): number {
  if (!tokens.length || !value) return 0;

  let matchedCount = 0;
  let score = 0;

  for (const token of tokens) {
    if (value === token) {
      score += baseScore + exactBonus;
      matchedCount += 1;
    } else if (value.startsWith(token)) {
      score += baseScore + startsWithBonus;
      matchedCount += 1;
    } else if (value.includes(token)) {
      score += baseScore + includesBonus;
      matchedCount += 1;
    }
  }

  if (matchedCount === 0) return 0;

  // Reward fields that satisfy more of the query tokens.
  return score + matchedCount * 25;
}

export function LandingHeroBanner({
  visiblePortals,
  onPortalSelect,
}: LandingHeroBannerProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  const [portalProjects, setPortalProjects] = useState<Project[]>([]);
  const [isProjectsLoading, setIsProjectsLoading] = useState(false);
  const [projectsError, setProjectsError] = useState<string | null>(null);

  const [iconMapping, setIconMapping] = useState<Record<string, string>>({});

  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  useEffect(() => {
    let cancelled = false;

    const loadProjects = async () => {
      setIsProjectsLoading(true);
      setProjectsError(null);

      try {
        const response = await fetchProjects();

        const visiblePortalKeys = new Set(
          visiblePortals
            .map((portal) => portal.projectKey?.toUpperCase())
            .filter((key): key is string => !!key)
        );

        const filteredProjects = (response.projects ?? []).filter((project) =>
          visiblePortalKeys.has(project.key?.toUpperCase())
        );

        if (!cancelled) {
          setPortalProjects(filteredProjects);
        }
      } catch (error) {
        console.error("Failed to fetch projects for landing hero search", error);
        if (!cancelled) {
          setProjectsError("Failed to load portal search metadata.");
          setPortalProjects([]);
        }
      } finally {
        if (!cancelled) {
          setIsProjectsLoading(false);
        }
      }
    };

    void loadProjects();

    return () => {
      cancelled = true;
    };
  }, [visiblePortals]);

  const searchTokens = useMemo(
    () => tokenizeQuery(debouncedSearchTerm).filter((token) => token.length >= 2),
    [debouncedSearchTerm]
  );

  const requestTypeQueries = useQueries({
    queries: searchTokens.map((token) => ({
      queryKey: ["request-types-global-search-token", token, SEARCH_LIMIT],
      queryFn: () => searchAllRequestTypes(token, SEARCH_LIMIT),
      enabled: token.length >= 2,
      staleTime: 2 * 60 * 1000,
    })),
  });

  const requestTypeResultsByToken = useMemo(() => {
    const merged = new Map<string, GlobalRequestTypeSearchResult>();

    for (const queryResult of requestTypeQueries) {
      const results = queryResult.data?.results ?? [];
      for (const result of results) {
        const key = `${result.projectKey}:${result.requestType.id}`;
        if (!merged.has(key)) {
          merged.set(key, result);
        }
      }
    }

    return Array.from(merged.values());
  }, [requestTypeQueries]);

  const isSearchingRequestTypes = requestTypeQueries.some((query) => query.isLoading);

  useEffect(() => {
    if (!requestTypeResultsByToken.length) {
      return;
    }

    const uniqueServiceDeskIds = [
      ...new Set(
        requestTypeResultsByToken
          .map((r) => r.serviceDeskId)
          .filter((id): id is string => !!id)
      ),
    ];

    if (uniqueServiceDeskIds.length === 0) return;

    let cancelled = false;

    const fetchAllIcons = async () => {
      const allMappings: Record<string, string> = {};

      await Promise.all(
        uniqueServiceDeskIds.map(async (serviceDeskId) => {
          const mapping = await fetchJsmRequestTypeIcons(serviceDeskId);
          Object.assign(allMappings, mapping);
        })
      );

      if (!cancelled) {
        setIconMapping(allMappings);
      }
    };

    void fetchAllIcons();

    return () => {
      cancelled = true;
    };
  }, [requestTypeResultsByToken]);

  const enrichedRequestTypeResults = useMemo(() => {
    if (Object.keys(iconMapping).length === 0) return requestTypeResultsByToken;

    return requestTypeResultsByToken.map((result) => ({
      ...result,
      requestType: {
        ...result.requestType,
        iconUrl: iconMapping[result.requestType.id] || result.requestType.iconUrl,
      },
    }));
  }, [requestTypeResultsByToken, iconMapping]);

  const projectsByKey = useMemo(() => {
    const map = new Map<string, Project>();
    for (const project of portalProjects) {
      if (project.key) {
        map.set(project.key.toUpperCase(), project);
      }
    }
    return map;
  }, [portalProjects]);

  const portalDocs = useMemo<PortalSearchDoc[]>(() => {
    return visiblePortals
      .filter((portal) => !!portal.projectKey && !!portal.projectName)
      .map((portal) => {
        const project = projectsByKey.get(portal.projectKey.toUpperCase());
        const description = project?.description ?? "";

        return {
          id: `portal:${portal.projectKey}`,
          type: "portal",
          portal,
          projectName: portal.projectName ?? "",
          projectKey: portal.projectKey ?? "",
          description,
          normalizedProjectName: normalizeSearchText(portal.projectName),
          normalizedProjectKey: normalizeSearchText(portal.projectKey),
          normalizedDescription: normalizeSearchText(description),
        };
      });
  }, [visiblePortals, projectsByKey]);

  const requestTypeDocs = useMemo<RequestTypeSearchDoc[]>(() => {
    return enrichedRequestTypeResults.map((result) => ({
      id: `requestType:${result.projectKey}:${result.requestType.id}`,
      type: "requestType",
      result,
      normalizedRequestTypeName: normalizeSearchText(result.requestType.name),
      normalizedProjectName: normalizeSearchText(result.projectName),
      normalizedProjectKey: normalizeSearchText(result.projectKey),
    }));
  }, [enrichedRequestTypeResults]);

  const portalDocsById = useMemo(() => {
    const map = new Map<string, PortalSearchDoc>();
    for (const doc of portalDocs) {
      map.set(doc.id, doc);
    }
    return map;
  }, [portalDocs]);

  const requestTypeDocsById = useMemo(() => {
    const map = new Map<string, RequestTypeSearchDoc>();
    for (const doc of requestTypeDocs) {
      map.set(doc.id, doc);
    }
    return map;
  }, [requestTypeDocs]);

  const portalNameIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of portalDocs) {
      if (doc.normalizedProjectName) {
        index.add(doc.id, doc.normalizedProjectName);
      }
    }
    return index;
  }, [portalDocs]);

  const portalNameFuzzyIndex = useMemo(() => {
    const index = new Index({
      tokenize: "forward",
      encoder: Charset.LatinBalance,
    });
    for (const doc of portalDocs) {
      if (doc.normalizedProjectName) {
        index.add(doc.id, doc.normalizedProjectName);
      }
    }
    return index;
  }, [portalDocs]);

  const portalKeyIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of portalDocs) {
      if (doc.normalizedProjectKey) {
        index.add(doc.id, doc.normalizedProjectKey);
      }
    }
    return index;
  }, [portalDocs]);

  const portalDescriptionIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of portalDocs) {
      if (doc.normalizedDescription) {
        index.add(doc.id, doc.normalizedDescription);
      }
    }
    return index;
  }, [portalDocs]);

  const portalDescriptionFuzzyIndex = useMemo(() => {
    const index = new Index({
      tokenize: "forward",
      encoder: Charset.LatinBalance,
    });
    for (const doc of portalDocs) {
      if (doc.normalizedDescription) {
        index.add(doc.id, doc.normalizedDescription);
      }
    }
    return index;
  }, [portalDocs]);

  const requestTypeNameIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of requestTypeDocs) {
      if (doc.normalizedRequestTypeName) {
        index.add(doc.id, doc.normalizedRequestTypeName);
      }
    }
    return index;
  }, [requestTypeDocs]);

  const requestTypeNameFuzzyIndex = useMemo(() => {
    const index = new Index({
      tokenize: "forward",
      encoder: Charset.LatinBalance,
    });
    for (const doc of requestTypeDocs) {
      if (doc.normalizedRequestTypeName) {
        index.add(doc.id, doc.normalizedRequestTypeName);
      }
    }
    return index;
  }, [requestTypeDocs]);

  const requestTypeProjectNameIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of requestTypeDocs) {
      if (doc.normalizedProjectName) {
        index.add(doc.id, doc.normalizedProjectName);
      }
    }
    return index;
  }, [requestTypeDocs]);

  const requestTypeProjectNameFuzzyIndex = useMemo(() => {
    const index = new Index({
      tokenize: "forward",
      encoder: Charset.LatinBalance,
    });
    for (const doc of requestTypeDocs) {
      if (doc.normalizedProjectName) {
        index.add(doc.id, doc.normalizedProjectName);
      }
    }
    return index;
  }, [requestTypeDocs]);

  const requestTypeProjectKeyIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
    for (const doc of requestTypeDocs) {
      if (doc.normalizedProjectKey) {
        index.add(doc.id, doc.normalizedProjectKey);
      }
    }
    return index;
  }, [requestTypeDocs]);

  const combinedResults = useMemo<SearchResultItem[]>(() => {
    const query = normalizeSearchText(debouncedSearchTerm);
    const tokens = tokenizeQuery(debouncedSearchTerm).filter((token) => token.length >= 2);
    if (tokens.length === 0) return [];

    const buildResults = (useFuzzy: boolean) => {
      const resultsMap = new Map<string, SearchResultItem>();

      const portalCandidateIds = new Set<string>();
      for (const token of tokens) {
        const portalNameMatches = useFuzzy
          ? (portalNameFuzzyIndex.search(token, { limit: FUZZY_SEARCH_LIMIT }) as string[])
          : (portalNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]);

        const portalDescriptionMatches = useFuzzy
          ? (portalDescriptionFuzzyIndex.search(token, { limit: FUZZY_SEARCH_LIMIT }) as string[])
          : (portalDescriptionIndex.search(token, { limit: SEARCH_LIMIT }) as string[]);

        portalNameMatches.forEach((id) => portalCandidateIds.add(String(id)));
        (portalKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
          portalCandidateIds.add(String(id))
        );
        portalDescriptionMatches.forEach((id) => portalCandidateIds.add(String(id)));
      }

      for (const id of portalCandidateIds) {
        const doc = portalDocsById.get(id);
        if (!doc) continue;

        const fields = [
          doc.normalizedProjectName,
          doc.normalizedProjectKey,
          doc.normalizedDescription,
        ];

        if (!matchesAllTokens(fields, query)) {
          continue;
        }

        const projectNameScore = getTokenAwareFieldScore(tokens, doc.normalizedProjectName, 3000, 500, 250, 100);
        const projectKeyScore = getTokenAwareFieldScore(tokens, doc.normalizedProjectKey, 2500, 400, 200, 90);
        const descriptionScore = getTokenAwareFieldScore(tokens, doc.normalizedDescription, 2000, 250, 100, 50);

        let matchedOn: "projectName" | "projectKey" | "description" = "projectName";
        let score = projectNameScore;

        if (projectKeyScore > score) {
          matchedOn = "projectKey";
          score = projectKeyScore;
        }

        if (descriptionScore > score) {
          matchedOn = "description";
          score = descriptionScore;
        }

        if (score <= 0) continue;

        resultsMap.set(doc.id, {
          id: doc.id,
          type: "portal",
          score: useFuzzy ? score - 200 : score,
          matchedOn,
          portal: doc.portal,
          projectName: doc.projectName,
          projectKey: doc.projectKey,
          description: doc.description,
        });
      }

      const requestTypeCandidateIds = new Set<string>();
      for (const token of tokens) {
        const requestTypeNameMatches = useFuzzy
          ? (requestTypeNameFuzzyIndex.search(token, { limit: FUZZY_SEARCH_LIMIT }) as string[])
          : (requestTypeNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]);

        const requestTypeProjectNameMatches = useFuzzy
          ? (requestTypeProjectNameFuzzyIndex.search(token, { limit: FUZZY_SEARCH_LIMIT }) as string[])
          : (requestTypeProjectNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]);

        requestTypeNameMatches.forEach((id) => requestTypeCandidateIds.add(String(id)));
        requestTypeProjectNameMatches.forEach((id) => requestTypeCandidateIds.add(String(id)));
        (requestTypeProjectKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
          requestTypeCandidateIds.add(String(id))
        );
      }

      for (const id of requestTypeCandidateIds) {
        const doc = requestTypeDocsById.get(id);
        if (!doc) continue;

        const fields = [
          doc.normalizedRequestTypeName,
          doc.normalizedProjectName,
          doc.normalizedProjectKey,
        ];

        if (!matchesAllTokens(fields, query)) {
          continue;
        }

        const requestTypeNameScore = getTokenAwareFieldScore(
          tokens,
          doc.normalizedRequestTypeName,
          1000,
          300,
          150,
          75
        );
        const projectNameScore = getTokenAwareFieldScore(
          tokens,
          doc.normalizedProjectName,
          900,
          250,
          120,
          60
        );
        const projectKeyScore = getTokenAwareFieldScore(
          tokens,
          doc.normalizedProjectKey,
          850,
          200,
          100,
          50
        );

        let matchedOn: "requestTypeName" | "projectName" | "projectKey" = "requestTypeName";
        let score = requestTypeNameScore;

        if (projectNameScore > score) {
          matchedOn = "projectName";
          score = projectNameScore;
        }

        if (projectKeyScore > score) {
          matchedOn = "projectKey";
          score = projectKeyScore;
        }

        if (score <= 0) continue;

        resultsMap.set(doc.id, {
          id: doc.id,
          type: "requestType",
          score: useFuzzy ? score - 200 : score,
          matchedOn,
          result: doc.result,
        });
      }

      return Array.from(resultsMap.values());
    };

    const exactResults = buildResults(false);
    const finalResults = exactResults.length > 0 ? exactResults : buildResults(true);

    return finalResults.sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;

      if (a.type === "portal" && b.type === "requestType") return -1;
      if (a.type === "requestType" && b.type === "portal") return 1;

      if (a.type === "portal" && b.type === "portal") {
        return a.projectName.localeCompare(b.projectName);
      }

      if (a.type === "requestType" && b.type === "requestType") {
        return a.result.requestType.name.localeCompare(b.result.requestType.name);
      }

      return 0;
    });
  }, [
    debouncedSearchTerm,
    portalNameIndex,
    portalNameFuzzyIndex,
    portalKeyIndex,
    portalDescriptionIndex,
    portalDescriptionFuzzyIndex,
    requestTypeNameIndex,
    requestTypeNameFuzzyIndex,
    requestTypeProjectNameIndex,
    requestTypeProjectNameFuzzyIndex,
    requestTypeProjectKeyIndex,
    portalDocsById,
    requestTypeDocsById,
  ]);

  const portalResults = useMemo(
    () => combinedResults.filter((item) => item.type === "portal"),
    [combinedResults]
  );

  const requestTypeResults = useMemo(
    () => combinedResults.filter((item) => item.type === "requestType"),
    [combinedResults]
  );

  const isSearching =
    searchTokens.length > 0 && (isProjectsLoading || isSearchingRequestTypes);

  const handleRequestTypeClick = (result: GlobalRequestTypeSearchResult) => {
    const url = buildRequestTypeUrl(
      result.isLive,
      result.projectKey,
      result.requestType.id,
      result.portalId
    );
    window.location.href = url;
  };

  return (
    <>
      <div
        className={`relative w-full overflow-hidden border-b ${FIXED_MIN_HEIGHT}`}
        style={{
          backgroundImage: `url(${HERO_BACKGROUND_IMAGE})`,
          backgroundSize: "cover",
          backgroundPosition: "center",
          backgroundRepeat: "no-repeat",
          backgroundColor: "#f8fafc",
        }}
      >
        <div className="absolute inset-0 bg-gradient-to-r from-black/40 via-black/30 to-black/20" />

        <div className="relative w-full h-full flex flex-col justify-center px-6 py-8 md:py-12">
          <div className="w-full max-w-5xl mx-auto space-y-6">
            <h1
              className="text-3xl md:text-4xl lg:text-5xl font-bold leading-tight text-white drop-shadow-lg"
              style={{ textShadow: "0 2px 4px rgba(0,0,0,0.3)" }}
            >
              {HERO_TITLE}
            </h1>
            <p
              className="text-base md:text-lg max-w-2xl leading-relaxed text-white/90"
              style={{ textShadow: "0 1px 2px rgba(0,0,0,0.2)" }}
            >
              {HERO_SUBTITLE}
            </p>

            <div className="w-full max-w-2xl mt-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-muted-foreground" />
                <Input
                  type="search"
                  readOnly
                  onClick={() => setIsSearchOpen(true)}
                  placeholder="Search portals, keywords, or request types..."
                  className="pl-10 pr-4 py-6 text-base bg-white/95 backdrop-blur-sm border-white/20 hover:bg-white focus:bg-white hover:border-primary/40 focus:border-primary transition-colors cursor-pointer shadow-lg"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      <Dialog open={isSearchOpen} onOpenChange={setIsSearchOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>Search Portals and Request Types</DialogTitle>
          </DialogHeader>

          <div className="relative mt-2">
            {isSearching ? (
              <Loader2 className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-muted-foreground animate-spin" />
            ) : (
              <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-muted-foreground" />
            )}
            <Input
              type="search"
              autoFocus
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search portals, keywords, or request types..."
              className="pl-10 pr-4 py-5 text-base"
            />
          </div>

          <div className="flex-1 overflow-y-auto mt-4 -mx-6 px-6">
            {projectsError && (
              <div className="mb-3 rounded-md border border-destructive/20 bg-destructive/5 px-3 py-2 text-sm text-destructive">
                {projectsError}
              </div>
            )}

            {isSearching ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">Searching...</span>
              </div>
            ) : combinedResults.length > 0 ? (
              <div className="space-y-6 pb-2">
                {portalResults.length > 0 && (
                  <div className="space-y-1">
                    <div className="px-1 pb-1">
                      <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                        Portals
                      </div>
                    </div>

                    {portalResults.map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        className="w-full flex items-start gap-3 px-3 py-3 text-left hover:bg-muted rounded-lg transition-colors cursor-pointer"
                        onClick={() => onPortalSelect(item.portal)}
                      >
                        <div className="flex-shrink-0 mt-0.5">
                          <div className="h-6 w-6 rounded bg-primary/10 flex items-center justify-center">
                            <FolderTree className="h-3.5 w-3.5 text-primary" />
                          </div>
                        </div>

                        <div className="flex-1 min-w-0">
                          <div className="flex items-center gap-2 flex-wrap">
                            <div className="font-medium text-sm text-foreground truncate">
                              {item.matchedOn === "projectName"
                                ? highlightText(item.projectName, debouncedSearchTerm)
                                : item.projectName}
                            </div>

                            <Badge variant="secondary" className="text-[10px]">
                              Portal
                            </Badge>

                            <Badge variant="outline" className="text-[10px]">
                              {item.matchedOn === "projectName"
                                ? "Matched on project name"
                                : item.matchedOn === "projectKey"
                                  ? "Matched on project key"
                                  : "Matched on keyword"}
                            </Badge>
                          </div>

                          <div className="text-xs text-muted-foreground truncate mt-0.5">
                            {item.matchedOn === "projectKey"
                              ? highlightText(item.projectKey, debouncedSearchTerm)
                              : item.projectKey}
                          </div>

                          {item.description && (
                            <div className="text-xs text-muted-foreground line-clamp-2 mt-1">
                              {item.matchedOn === "description"
                                ? getHighlightedSnippet(item.description, debouncedSearchTerm)
                                : item.description}
                            </div>
                          )}
                        </div>

                        <ArrowRight className="h-4 w-4 text-muted-foreground flex-shrink-0 mt-1" />
                      </button>
                    ))}
                  </div>
                )}

                {requestTypeResults.length > 0 && (
                  <div className="space-y-1">
                    <div className="px-1 pb-1">
                      <div className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                        Request Types
                      </div>
                    </div>

                    {requestTypeResults.map((item) => {
                      const result = item.result;

                      return (
                        <button
                          key={item.id}
                          type="button"
                          className="w-full flex items-start gap-3 px-3 py-3 text-left hover:bg-muted rounded-lg transition-colors cursor-pointer"
                          onClick={() => handleRequestTypeClick(result)}
                        >
                          <div className="flex-shrink-0 mt-0.5">
                            {result.requestType.iconUrl ? (
                              <img
                                src={result.requestType.iconUrl}
                                alt=""
                                className="h-6 w-6 rounded"
                              />
                            ) : (
                              <div className="h-6 w-6 rounded bg-primary/10 flex items-center justify-center">
                                <FileText className="h-3.5 w-3.5 text-primary" />
                              </div>
                            )}
                          </div>

                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 flex-wrap">
                              <div className="font-medium text-sm text-foreground truncate">
                                {highlightText(result.requestType.name, debouncedSearchTerm)}
                              </div>

                              <Badge variant="secondary" className="text-[10px]">
                                Request Type
                              </Badge>
                            </div>

                            <div className="text-xs text-muted-foreground truncate mt-0.5">
                              <>
                                {highlightText(result.projectName, debouncedSearchTerm)}
                                <span> (</span>
                                {highlightText(result.projectKey, debouncedSearchTerm)}
                                <span>)</span>
                              </>
                            </div>
                          </div>

                          <ArrowRight className="h-4 w-4 text-muted-foreground flex-shrink-0 mt-1" />
                        </button>
                      );
                    })}
                  </div>
                )}
              </div>
            ) : debouncedSearchTerm.length >= 2 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                No portals or request types found for &quot;{debouncedSearchTerm}&quot;
              </div>
            ) : (
              <div className="py-8 text-center text-sm text-muted-foreground">
                Type at least 2 characters to search
              </div>
            )}
          </div>
        </DialogContent>
      </Dialog>
    </>
  );
}
