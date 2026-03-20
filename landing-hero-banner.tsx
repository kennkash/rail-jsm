// /rail-at-sas/frontend/components/landing/landing-hero-banner.tsx
"use client";

import { useState, useEffect, useMemo } from "react";
import { Input } from "@/components/ui/input";
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Search, Loader2, FileText, ArrowRight } from "lucide-react";
import { useGlobalRequestTypeSearch } from "@/hooks/use-request-types";
import { fetchJsmRequestTypeIcons } from "@/lib/api/jira-search-client";
import { buildRequestTypeUrl } from "@/types/router";
import type { GlobalRequestTypeSearchResult } from "@/lib/api/request-types-client";

// Static configuration - hard-coded values
const HERO_TITLE = "Samsung Customer Request Portal";
const HERO_SUBTITLE = "Search for the right portal, then submit your request";
const FIXED_MIN_HEIGHT = "min-h-[18rem]";
const HERO_BACKGROUND_IMAGE = "https://jira.samsungaustin.com/secure/attachment/503395/503395_sas_building2-resized.jpg";

export function LandingHeroBanner() {
  // Search dialog state
  const [searchTerm, setSearchTerm] = useState("");
  const [debouncedSearchTerm, setDebouncedSearchTerm] = useState("");
  const [isSearchOpen, setIsSearchOpen] = useState(false);

  // Icon mapping for request types (fetched from JSM API)
  const [iconMapping, setIconMapping] = useState<Record<string, string>>({});

  // Debounce search term
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedSearchTerm(searchTerm);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  // Global search query
  const { data: searchResults, isLoading: isSearching } = useGlobalRequestTypeSearch(debouncedSearchTerm);

  // Fetch icons for all unique service desks in the results
  useEffect(() => {
    if (!searchResults?.results?.length) {
      return;
    }

    // Get unique service desk IDs from results
    const uniqueServiceDeskIds = [...new Set(
      searchResults.results
        .map(r => r.serviceDeskId)
        .filter((id): id is string => !!id)
    )];

    if (uniqueServiceDeskIds.length === 0) return;

    let cancelled = false;

    const fetchAllIcons = async () => {
      const allMappings: Record<string, string> = {};

      // Fetch icons for each service desk in parallel
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

    fetchAllIcons();

    return () => {
      cancelled = true;
    };
  }, [searchResults?.results]);

  // Enrich results with icon URLs
  const enrichedResults = useMemo(() => {
    if (!searchResults?.results) return [];
    if (Object.keys(iconMapping).length === 0) return searchResults.results;

    return searchResults.results.map(result => ({
      ...result,
      requestType: {
        ...result.requestType,
        iconUrl: iconMapping[result.requestType.id] || result.requestType.iconUrl,
      },
    }));
  }, [searchResults?.results, iconMapping]);

  const handleResultClick = (result: GlobalRequestTypeSearchResult) => {
    // Navigate to the appropriate portal based on portal type
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
          backgroundSize: 'cover',
          backgroundPosition: 'center',
          backgroundRepeat: 'no-repeat',
          backgroundColor: '#f8fafc', // slate-50 fallback
        }}
      >
        {/* Dark overlay for text readability */}
        <div className="absolute inset-0 bg-gradient-to-r from-black/40 via-black/30 to-black/20" />

        {/* Content layer */}
        <div className="relative w-full h-full flex flex-col justify-center px-6 py-8 md:py-12">
          <div className="w-full max-w-5xl mx-auto space-y-6">
            <h1
              className="text-3xl md:text-4xl lg:text-5xl font-bold leading-tight text-white drop-shadow-lg"
              style={{ textShadow: '0 2px 4px rgba(0,0,0,0.3)' }}
            >
              {HERO_TITLE}
            </h1>
            <p
              className="text-base md:text-lg max-w-2xl leading-relaxed text-white/90"
              style={{ textShadow: '0 1px 2px rgba(0,0,0,0.2)' }}
            >
              {HERO_SUBTITLE}
            </p>

            {/* Search Bar - opens dialog on click */}
            <div className="w-full max-w-2xl mt-4">
              <div className="relative">
                <Search className="absolute left-3 top-1/2 transform -translate-y-1/2 h-5 w-5 text-muted-foreground" />
                <Input
                  type="search"
                  readOnly
                  onClick={() => setIsSearchOpen(true)}
                  placeholder="Search for request types across all portals..."
                  className="pl-10 pr-4 py-6 text-base bg-white/95 backdrop-blur-sm border-white/20 hover:bg-white focus:bg-white hover:border-primary/40 focus:border-primary transition-colors cursor-pointer shadow-lg"
                />
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Search Dialog - renders in portal, no z-index issues */}
      <Dialog open={isSearchOpen} onOpenChange={setIsSearchOpen}>
        <DialogContent className="sm:max-w-2xl max-h-[80vh] overflow-hidden flex flex-col">
          <DialogHeader>
            <DialogTitle>Search Request Types</DialogTitle>
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
              placeholder="Search for request types across all portals..."
              className="pl-10 pr-4 py-5 text-base"
            />
          </div>

          {/* Search Results */}
          <div className="flex-1 overflow-y-auto mt-4 -mx-6 px-6">
            {isSearching ? (
              <div className="flex items-center justify-center py-8">
                <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
                <span className="ml-2 text-sm text-muted-foreground">Searching...</span>
              </div>
            ) : enrichedResults.length > 0 ? (
              <div className="space-y-1">
                {enrichedResults.map((result, index) => (
                  <button
                    key={`${result.projectKey}-${result.requestType.id}-${index}`}
                    type="button"
                    className="w-full flex items-start gap-3 px-3 py-3 text-left hover:bg-muted rounded-lg transition-colors cursor-pointer"
                    onClick={() => handleResultClick(result)}
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
                      <div className="font-medium text-sm text-foreground truncate">
                        {result.requestType.name}
                      </div>
                      <div className="text-xs text-muted-foreground truncate">
                        {result.projectName}
                      </div>
                    </div>
                    <ArrowRight className="h-4 w-4 text-muted-foreground flex-shrink-0 mt-1" />
                  </button>
                ))}
              </div>
            ) : debouncedSearchTerm.length >= 2 ? (
              <div className="py-8 text-center text-sm text-muted-foreground">
                No request types found for &quot;{debouncedSearchTerm}&quot;
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
