"use client";
import { useState, useEffect, useMemo, type ReactNode } from "react";
import { useQueries } from "@tanstack/react-query";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Badge } from "@/components/ui/badge";
import { Search, Loader2, FileText, ArrowRight, FolderTree } from "lucide-react";
import { Index } from "flexsearch";
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
const HERO_BACKGROUND_IMAGE = "https://jira.samsungaustin.com/secure/attachment/503395/503395
const SEARCH_LIMIT = 20;
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
function getTextPartsWithHighlights(text: string, query: string): Array<{ text: string; highl
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
<mark key={`${part.text}-${index}`} className="rounded bg-yellow-200/70 px-0.5 text
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
// Fuzzy / Levenshtein utilities
function levenshteinDistance(a: string, b: string): number {
if (a === b) return 0;
if (a.length === 0) return b.length;
if (b.length === 0) return a.length;
const prev = Array.from({ length: b.length + 1 }, (_, i) => i);
const curr = new Array<number>(b.length + 1);
for (let i = 1; i <= a.length; i++) {
curr[0] = i;
for (let j = 1; j <= b.length; j++) {
const cost = a[i - 1] === b[j - 1] ? 0 : 1;
curr[j] = Math.min(prev[j] + 1, curr[j - 1] + 1, prev[j - 1] + cost);
}
prev.splice(0, prev.length, ...curr);
}
return prev[b.length];
}
// Returns the max edit distance tolerated for a token of a given length.
// Tokens <= 3 chars are too short for fuzzy (too many false positives).
// Tokens 4-5 chars tolerate 1 edit (e.g. "Jera" -> "Jira").
// Tokens 6+ chars tolerate 2 edits (e.g. "Kanbaord" -> "Kanboard").
function getFuzzyMaxDistance(tokenLength: number): number {
if (tokenLength <= 3) return 0;
if (tokenLength <= 5) return 1;
return 2;
}
// Returns true if the token fuzzy-matches any whitespace-separated word in the field.
// Falls back to exact substring first for performance.
function fuzzyTokenMatchesField(token: string, field: string): boolean {
if (field.includes(token)) return true;
const maxDist = getFuzzyMaxDistance(token.length);
if (maxDist === 0) return false;
return field
.split(" ")
.some(
(word) =>
Math.abs(word.length - token.length) <= maxDist &&
levenshteinDistance(token, word) <= maxDist
);
}
// Like matchesAllTokens, but also accepts fuzzy hits.
// Returns how many tokens were matched only via fuzzy (for score penalisation).
function matchesAllTokensFuzzy(
fields: string[],
query: string
): { matched: boolean; fuzzyCount: number } {
const tokens = tokenizeQuery(query).filter((t) => t.length >= 2);
if (!tokens.length) return { matched: false, fuzzyCount: 0 };
let fuzzyCount = 0;
const allMatch = tokens.every((token) => {
const exactHit = fields.some((f) => f.includes(token));
if (exactHit) return true;
const fuzzyHit = fields.some((f) => fuzzyTokenMatchesField(token, f));
if (fuzzyHit) fuzzyCount++;
return fuzzyHit;
});
return { matched: allMatch, fuzzyCount };
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
if (!cancelled) {
setIconMapping(allMappings);
);
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
(portalKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id) portalCandidateIds.add(String(id))
=>
);
(portalDescriptionIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id
portalCandidateIds.add(String(id))
);
}
// Expand candidates to all portal docs for tokens long enough for fuzzy matching.
// FlexSearch only returns exact/prefix hits, so misspelled tokens produce no
// candidates — the full-doc scan below catches those cases.
const hasFuzzyableTokens = tokens.some((t) => getFuzzyMaxDistance(t.length) > 0);
if (hasFuzzyableTokens) {
for (const doc of portalDocs) portalCandidateIds.add(doc.id);
}
for (const id of portalCandidateIds) {
const doc = portalDocsById.get(id);
if (!doc) continue;
const fields = [
doc.normalizedProjectName,
doc.normalizedProjectKey,
doc.normalizedDescription,
];
const { matched: portalMatched, fuzzyCount: portalFuzzyCount } =
matchesAllTokensFuzzy(fields, query);
if (!portalMatched) continue;
const projectNameScore = getTokenAwareFieldScore(tokens, doc.normalizedProjectName, 300
const projectKeyScore = getTokenAwareFieldScore(tokens, doc.normalizedProjectKey, 2500,
const descriptionScore = getTokenAwareFieldScore(tokens, doc.normalizedDescription, 200
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
// Penalise fuzzy-matched results so exact matches always rank above them.
const portalFuzzyPenalty = portalFuzzyCount > 0 ? 0.45 : 1;
const portalFinalScore = score * portalFuzzyPenalty;
if (portalFinalScore <= 0) continue;
resultsMap.set(doc.id, {
id: doc.id,
type: "portal",
score: portalFinalScore,
matchedOn,
portal: doc.portal,
projectName: doc.projectName,
projectKey: doc.projectKey,
description: doc.description,
});
}
const requestTypeCandidateIds = new Set<string>();
for (const token of tokens) {
(requestTypeNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach((id)
requestTypeCandidateIds.add(String(id))
);
(requestTypeProjectNameIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEac
requestTypeCandidateIds.add(String(id))
);
(requestTypeProjectKeyIndex.search(token, { limit: SEARCH_LIMIT }) as string[]).forEach
requestTypeCandidateIds.add(String(id))
);
}
// Same fuzzy candidate expansion for request types.
if (hasFuzzyableTokens) {
for (const doc of requestTypeDocs) requestTypeCandidateIds.add(doc.id);
}
for (const id of requestTypeCandidateIds) {
const doc = requestTypeDocsById.get(id);
if (!doc) continue;
const fields = [
doc.normalizedRequestTypeName,
doc.normalizedProjectName,
doc.normalizedProjectKey,
];
const { matched: rtMatched, fuzzyCount: rtFuzzyCount } =
matchesAllTokensFuzzy(fields, query);
if (!rtMatched) continue;
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
// Penalise fuzzy-matched results so exact matches always rank above them.
const rtFuzzyPenalty = rtFuzzyCount > 0 ? 0.45 : 1;
const rtFinalScore = score * rtFuzzyPenalty;
if (rtFinalScore <= 0) continue;
resultsMap.set(doc.id, {
id: doc.id,
type: "requestType",
score: rtFinalScore,
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
portalDocsById,
requestTypeDocsById,
portalDocs,
requestTypeDocs,
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
<div className="absolute inset-0 bg-gradient-to-r from-black/40 via-black/30 to-black
<div className="relative w-full h-full flex flex-col justify-center px-6 py-8 md:py-1
<div className="w-full max-w-5xl mx-auto space-y-6">
<h1
className="text-3xl md:text-4xl lg:text-5xl font-bold leading-tight text-white
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
<Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5
<Input
type="search"
readOnly
onClick={() => setIsSearchOpen(true)}
placeholder="Search portals, keywords, or request types..."
className="pl-10 pr-4 py-6 text-base bg-white/95 backdrop-blur-sm border-wh
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
<Loader2 className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5
) : (
<Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 t
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
<div className="mb-3 rounded-md border border-destructive/20 bg-destructive/5 p
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
<div className="text-xs font-semibold uppercase tracking-wide text-mute
Portals
</div>
</div>
{portalResults.map((item) => (
<button
key={item.id}
type="button"
className="w-full flex items-start gap-3 px-3 py-3 text-left hover:bg
onClick={() => onPortalSelect(item.portal)}
>
<div className="flex-shrink-0 mt-0.5">
<div className="h-6 w-6 rounded bg-primary/10 flex items-center jus
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
mt-1">
{item.description && (
<div className="text-xs text-muted-foreground line-clamp-2 {item.matchedOn === "description"
? getHighlightedSnippet(item.description, debouncedSearchTerm
: item.description}
</div>
)}
</div>
<ArrowRight className="h-4 w-4 text-muted-foreground flex-shrink-0 mt
</button>
))}
</div>
)}
{requestTypeResults.length > 0 && (
<div className="space-y-1">
<div className="px-1 pb-1">
<div className="text-xs font-semibold uppercase tracking-wide text-mute
Request Types
</div>
</div>
{requestTypeResults.map((item) => {
const result = item.result;
return (
<button
key={item.id}
type="button"
className="w-full flex items-start gap-3 px-3 py-3 text-left onClick={() => handleRequestTypeClick(result)}
hover:
>
<div className="flex-shrink-0 mt-0.5">
{result.requestType.iconUrl ? (
<img
src={result.requestType.iconUrl}
alt=""
className="h-6 w-6 rounded"
/>
) : (
<div className="h-6 w-6 rounded bg-primary/10 flex items-center
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
<ArrowRight className="h-4 w-4 text-muted-foreground flex-shrink-0
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
