// /rail-at-sas/frontend/components/landing/landing-hero-banner.tsx

{shouldShowTabs && (
  <div className="sticky top-0 z-10 bg-background pb-2">
    <Tabs
      value={activeTab}
      onValueChange={(value) => setActiveTab(value as SearchTab)}
    >
      <TabsList>
        <TabsTrigger
          value="portal"
          disabled={!hasPortalResults}
          className="cursor-pointer disabled:cursor-not-allowed"
        >
          Portals
          <span className="ml-1.5 text-xs">({portalResults.length})</span>
        </TabsTrigger>

        <TabsTrigger
          value="requestType"
          disabled={!hasRequestTypeResults}
          className="cursor-pointer disabled:cursor-not-allowed"
        >
          Request Types
          <span className="ml-1.5 text-xs">({requestTypeResults.length})</span>
        </TabsTrigger>
      </TabsList>
    </Tabs>
  </div>
)}

// /rail-at-sas/frontend/components/landing/landing-hero-banner.tsx
"use client";

import { useState, useEffect, useMemo, type ReactNode } from "react";
import { useQueries } from "@tanstack/react-query";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Search, Loader2, FileText, ArrowRight, FolderTree } from "lucide-react";
import { Index } from "flexsearch";
import { distance as levenshteinDistance } from "fastest-levenshtein";
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
const HERO_BACKGROUND_IMAGE =
  "https://jira.samsungaustin.com/secure/attachment/503395/503395_sas_building2-resized.jpg";
const SEARCH_LIMIT = 20;

const FUZZY_MIN_TOKEN_LENGTH = 3;
const FUZZY_MAX_EDIT_DISTANCE_SHORT = 1;
const FUZZY_MAX_EDIT_DISTANCE_LONG = 2;
const FUZZY_MIN_SIMILARITY = 0.72;

type LandingHeroBannerProps = {
  visiblePortals: PortalInfo[];
  onPortalSelect: (portal: PortalInfo) => void;
};

type SearchTab = "portal" | "requestType";

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

function getMaxEditDistance(token: string): number {
  if (token.length <= 4) return FUZZY_MAX_EDIT_DISTANCE_SHORT;
  return FUZZY_MAX_EDIT_DISTANCE_LONG;
}

function getSimilarity(a: string, b: string): number {
  if (!a || !b) return 0;
  if (a === b) return 1;

  const dist = levenshteinDistance(a, b);
  const maxLen = Math.max(a.length, b.length);
  return maxLen === 0 ? 1 : 1 - dist / maxLen;
}

function fieldMatchesToken(field: string, token: string): boolean {
  if (!field || !token) return false;

  if (field.includes(token)) return true;

  const fieldTokens = tokenizeQuery(field);

  for (const fieldToken of fieldTokens) {
    if (fieldToken.includes(token)) return true;

    if (
      token.length >= FUZZY_MIN_TOKEN_LENGTH &&
      fieldToken.length >= FUZZY_MIN_TOKEN_LENGTH
    ) {
      const dist = levenshteinDistance(token, fieldToken);
      const similarity = getSimilarity(token, fieldToken);

      if (
        dist <= getMaxEditDistance(token) &&
        similarity >= FUZZY_MIN_SIMILARITY
      ) {
        return true;
      }
    }
  }

  return false;
}

function matchesAllTokensFuzzy(fields: string[], query: string): boolean {
  const tokens = tokenizeQuery(query);
  if (!tokens.length) return false;

  return tokens.every((token) =>
    fields.some((field) => fieldMatchesToken(field, token))
  );
}

function getBestFuzzyTokenScore(
  queryTokens: string[],
  fieldValue: string,
  baseScore: number,
  exactBonus: number,
  prefixBonus: number,
  includesBonus: number,
  fuzzyBonus: number
): number {
  if (!queryTokens.length || !fieldValue) return 0;

  const fieldTokens = tokenizeQuery(fieldValue);
  if (!fieldTokens.length) return 0;

  let matchedCount = 0;
  let score = 0;

  for (const queryToken of queryTokens) {
    let bestTokenScore = 0;

    for (const fieldToken of fieldTokens) {
      if (fieldToken === queryToken) {
        bestTokenScore = Math.max(bestTokenScore, baseScore + exactBonus);
        continue;
      }

      if (fieldToken.startsWith(queryToken)) {
        bestTokenScore = Math.max(bestTokenScore, baseScore + prefixBonus);
        continue;
      }

      if (fieldToken.includes(queryToken)) {
        bestTokenScore = Math.max(bestTokenScore, baseScore + includesBonus);
        continue;
      }

      if (
        queryToken.length >= FUZZY_MIN_TOKEN_LENGTH &&
        fieldToken.length >= FUZZY_MIN_TOKEN_LENGTH
      ) {
        const dist = levenshteinDistance(queryToken, fieldToken);
        const similarity = getSimilarity(queryToken, fieldToken);

        if (
          dist <= getMaxEditDistance(queryToken) &&
          similarity >= FUZZY_MIN_SIMILARITY
        ) {
          const fuzzyScore =
            baseScore + fuzzyBonus + Math.round(similarity * 40) - dist * 10;

          bestTokenScore = Math.max(bestTokenScore, fuzzyScore);
        }
      }
    }

    if (bestTokenScore > 0) {
      matchedCount += 1;
      score += bestTokenScore;
    }
  }

  if (matchedCount === 0) return 0;

  return score + matchedCount * 20;
}

function addPortalFuzzyCandidates(
  docs: PortalSearchDoc[],
  tokens: string[],
  candidateIds: Set<string>
): void {
  for (const doc of docs) {
    const fields = [
      doc.normalizedProjectName,
      doc.normalizedProjectKey,
      doc.normalizedDescription,
    ];

    const matched = tokens.every((token) =>
      fields.some((field) => fieldMatchesToken(field, token))
    );

    if (matched) {
      candidateIds.add(doc.id);
    }
  }
}

function addRequestTypeFuzzyCandidates(
  docs: RequestTypeSearchDoc[],
  tokens: string[],
  candidateIds: Set<string>
): void {
  for (const doc of docs) {
    const fields = [
      doc.normalizedRequestTypeName,
      doc.normalizedProjectName,
      doc.normalizedProjectKey,
    ];

    const matched = tokens.every((token) =>
      fields.some((field) => fieldMatchesToken(field, token))
    );

    if (matched) {
      candidateIds.add(doc.id);
    }
  }
}

type HighlightRange = {
  start: number;
  end: number;
};

function getTextTokenRanges(text: string): Array<{ token: string; start: number; end: number }> {
  const ranges: Array<{ token: string; start: number; end: number }> = [];
  const regex = /\p{L}[\p{L}\p{N}]*/gu;

  for (const match of text.matchAll(regex)) {
    const token = match[0];
    const start = match.index ?? 0;
    const end = start + token.length;

    ranges.push({
      token,
      start,
      end,
    });
  }

  return ranges;
}

function getBestHighlightRangeForToken(text: string, queryToken: string): HighlightRange | null {
  if (!text?.trim() || !queryToken) return null;

  const lowerText = text.toLowerCase();
  const lowerQueryToken = queryToken.toLowerCase();

  const exactIndex = lowerText.indexOf(lowerQueryToken);
  if (exactIndex !== -1) {
    return {
      start: exactIndex,
      end: exactIndex + queryToken.length,
    };
  }

  const tokenRanges = getTextTokenRanges(text);

  let best:
    | {
        start: number;
        end: number;
        score: number;
      }
    | null = null;

  for (const range of tokenRanges) {
    const normalizedFieldToken = normalizeSearchText(range.token);
    if (!normalizedFieldToken) continue;

    if (normalizedFieldToken === lowerQueryToken) {
      return { start: range.start, end: range.end };
    }

    let score = -1;

    if (normalizedFieldToken.startsWith(lowerQueryToken)) {
      score = 1000 + normalizedFieldToken.length;
    } else if (normalizedFieldToken.includes(lowerQueryToken)) {
      score = 800 + normalizedFieldToken.length;
    } else if (
      lowerQueryToken.length >= FUZZY_MIN_TOKEN_LENGTH &&
      normalizedFieldToken.length >= FUZZY_MIN_TOKEN_LENGTH
    ) {
      const dist = levenshteinDistance(lowerQueryToken, normalizedFieldToken);
      const similarity = getSimilarity(lowerQueryToken, normalizedFieldToken);

      if (
        dist <= getMaxEditDistance(lowerQueryToken) &&
        similarity >= FUZZY_MIN_SIMILARITY
      ) {
        score = 500 + Math.round(similarity * 100) - dist * 25;
      }
    }

    if (score > -1 && (!best || score > best.score)) {
      best = {
        start: range.start,
        end: range.end,
        score,
      };
    }
  }

  return best ? { start: best.start, end: best.end } : null;
}

function mergeHighlightRanges(ranges: HighlightRange[]): HighlightRange[] {
  if (!ranges.length) return [];

  const sorted = [...ranges].sort((a, b) => a.start - b.start);
  const merged: HighlightRange[] = [sorted[0]];

  for (let i = 1; i < sorted.length; i += 1) {
    const current = sorted[i];
    const last = merged[merged.length - 1];

    if (current.start <= last.end) {
      last.end = Math.max(last.end, current.end);
    } else {
      merged.push({ ...current });
    }
  }

  return merged;
}

function getFuzzyHighlightRanges(text: string, query: string): HighlightRange[] {
  if (!text?.trim()) return [];

  const queryTokens = tokenizeQuery(query).filter(Boolean);
  if (!queryTokens.length) return [];

  const ranges = queryTokens
    .map((token) => getBestHighlightRangeForToken(text, token))
    .filter((range): range is HighlightRange => range !== null);

  return mergeHighlightRanges(ranges);
}

function getTextPartsWithFuzzyHighlights(
  text: string,
  query: string
): Array<{ text: string; highlight: boolean }> {
  if (!text?.trim()) {
    return [{ text, highlight: false }];
  }

  const ranges = getFuzzyHighlightRanges(text, query);
  if (!ranges.length) {
    return [{ text, highlight: false }];
  }

  const parts: Array<{ text: string; highlight: boolean }> = [];
  let cursor = 0;

  for (const range of ranges) {
    if (range.start > cursor) {
      parts.push({
        text: text.slice(cursor, range.start),
        highlight: false,
      });
    }

    parts.push({
      text: text.slice(range.start, range.end),
      highlight: true,
    });

    cursor = range.end;
  }

  if (cursor < text.length) {
    parts.push({
      text: text.slice(cursor),
      highlight: false,
    });
  }

  return parts;
}

function getTextPartsWithHighlights(
  text: string,
  query: string
): Array<{ text: string; highlight: boolean }> {
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
  const parts = getTextPartsWithFuzzyHighlights(text, query);

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

  const ranges = getFuzzyHighlightRanges(text, query);
  if (!ranges.length) return text;

  const firstRange = ranges[0];
  const start = Math.max(0, firstRange.start - radius);
  const end = Math.min(text.length, firstRange.end + radius);

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

export function LandingHeroBanner({
  visiblePortals,
  onPortalSelect,
}: LandingHeroBannerProps) {
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const [activeTab, setActiveTab] = useState<SearchTab>("portal");

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

  const requestTypeNameIndex = useMemo(() => {
    const index = new Index({ tokenize: "forward" });
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

    const resultsMap = new Map<string, SearchResultItem>();

    const portalCandidateIds = new Set<string>();
    for (const token of tokens) {
      (portalNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        portalCandidateIds.add(String(id))
      );
      (portalKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        portalCandidateIds.add(String(id))
      );
      (portalDescriptionIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        portalCandidateIds.add(String(id))
      );
    }

    if (portalCandidateIds.size < SEARCH_LIMIT) {
      addPortalFuzzyCandidates(portalDocs, tokens, portalCandidateIds);
    }

    for (const id of portalCandidateIds) {
      const doc = portalDocsById.get(id);
      if (!doc) continue;

      const fields = [
        doc.normalizedProjectName,
        doc.normalizedProjectKey,
        doc.normalizedDescription,
      ];

      if (!matchesAllTokensFuzzy(fields, query)) {
        continue;
      }

      const projectNameScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedProjectName,
        3000,
        500,
        250,
        100,
        40
      );

      const projectKeyScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedProjectKey,
        2500,
        400,
        200,
        90,
        35
      );

      const descriptionScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedDescription,
        2000,
        250,
        100,
        50,
        20
      );

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
        score,
        matchedOn,
        portal: doc.portal,
        projectName: doc.projectName,
        projectKey: doc.projectKey,
        description: doc.description,
      });
    }

    const requestTypeCandidateIds = new Set<string>();
    for (const token of tokens) {
      (requestTypeNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        requestTypeCandidateIds.add(String(id))
      );
      (requestTypeProjectNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        requestTypeCandidateIds.add(String(id))
      );
      (requestTypeProjectKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) =>
        requestTypeCandidateIds.add(String(id))
      );
    }

    if (requestTypeCandidateIds.size < SEARCH_LIMIT) {
      addRequestTypeFuzzyCandidates(requestTypeDocs, tokens, requestTypeCandidateIds);
    }

    for (const id of requestTypeCandidateIds) {
      const doc = requestTypeDocsById.get(id);
      if (!doc) continue;

      const fields = [
        doc.normalizedRequestTypeName,
        doc.normalizedProjectName,
        doc.normalizedProjectKey,
      ];

      if (!matchesAllTokensFuzzy(fields, query)) {
        continue;
      }

      const requestTypeNameScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedRequestTypeName,
        1000,
        300,
        150,
        75,
        25
      );

      const projectNameScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedProjectName,
        900,
        250,
        120,
        60,
        20
      );

      const projectKeyScore = getBestFuzzyTokenScore(
        tokens,
        doc.normalizedProjectKey,
        850,
        200,
        100,
        50,
        15
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
        score,
        matchedOn,
        result: doc.result,
      });
    }

    return Array.from(resultsMap.values()).sort((a, b) => {
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
    portalKeyIndex,
    portalDescriptionIndex,
    requestTypeNameIndex,
    requestTypeProjectNameIndex,
    requestTypeProjectKeyIndex,
    portalDocs,
    requestTypeDocs,
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

  const hasPortalResults = portalResults.length > 0;
  const hasRequestTypeResults = requestTypeResults.length > 0;
  const hasSearchResults = hasPortalResults || hasRequestTypeResults;

  const isSearching =
    searchTokens.length > 0 && (isProjectsLoading || isSearchingRequestTypes);

  const shouldShowTabs =
    debouncedSearchTerm.length >= 2 && !isSearching && hasSearchResults;

  useEffect(() => {
    if (isSearching) return;

    if (hasPortalResults) {
      setActiveTab("portal");
      return;
    }

    if (hasRequestTypeResults) {
      setActiveTab("requestType");
      return;
    }

    setActiveTab("portal");
  }, [hasPortalResults, hasRequestTypeResults, isSearching, debouncedSearchTerm]);

  const handleRequestTypeClick = (result: GlobalRequestTypeSearchResult) => {
    const url = buildRequestTypeUrl(
      result.isLive,
      result.projectKey,
      result.requestType.id,
      result.portalId
    );
    window.location.href = url;
  };

  const renderPortalResult = (item: Extract<SearchResultItem, { type: "portal" }>) => (
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
  );

  const renderRequestTypeResult = (
    item: Extract<SearchResultItem, { type: "requestType" }>
  ) => {
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
            ) : debouncedSearchTerm.length >= 2 ? (
              hasSearchResults ? (
                <div className="space-y-4 pb-2">
                  {shouldShowTabs && (
                    <div className="sticky top-0 z-10 bg-background pb-2">
                      <div className="inline-flex rounded-lg border bg-muted/40 p-1">
                        <button
                          type="button"
                          disabled={!hasPortalResults}
                          onClick={() => hasPortalResults && setActiveTab("portal")}
                          className={[
                            "px-3 py-1.5 text-sm font-medium rounded-md transition-colors",
                            activeTab === "portal" && hasPortalResults
                              ? "bg-background text-foreground shadow-sm"
                              : "text-muted-foreground",
                            !hasPortalResults
                              ? "cursor-not-allowed opacity-50"
                              : "hover:bg-background/70",
                          ].join(" ")}
                        >
                          Portals
                          <span className="ml-1.5 text-xs">({portalResults.length})</span>
                        </button>

                        <button
                          type="button"
                          disabled={!hasRequestTypeResults}
                          onClick={() => hasRequestTypeResults && setActiveTab("requestType")}
                          className={[
                            "px-3 py-1.5 text-sm font-medium rounded-md transition-colors",
                            activeTab === "requestType" && hasRequestTypeResults
                              ? "bg-background text-foreground shadow-sm"
                              : "text-muted-foreground",
                            !hasRequestTypeResults
                              ? "cursor-not-allowed opacity-50"
                              : "hover:bg-background/70",
                          ].join(" ")}
                        >
                          Request Types
                          <span className="ml-1.5 text-xs">({requestTypeResults.length})</span>
                        </button>
                      </div>
                    </div>
                  )}

                  <div className="space-y-1">
                    {activeTab === "portal"
                      ? portalResults.map((item) =>
                          renderPortalResult(item as Extract<SearchResultItem, { type: "portal" }>)
                        )
                      : requestTypeResults.map((item) =>
                          renderRequestTypeResult(
                            item as Extract<SearchResultItem, { type: "requestType" }>
                          )
                        )}
                  </div>
                </div>
              ) : (
                <div className="py-8 text-center text-sm text-muted-foreground">
                  No portals or request types found for &quot;{debouncedSearchTerm}&quot;
                </div>
              )
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
