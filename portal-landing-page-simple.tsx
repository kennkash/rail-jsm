// /mnt/k.kashmiry/git/rail-at-sas/frontend/components/landing/portal-landing-page-simple.tsx

"use client";

import { useCallback, useMemo, useState, useEffect } from "react";
import { useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { motion, AnimatePresence } from "framer-motion";
import { Clock, FolderTree, Search, Loader2, ListTodo, CheckCircle, ClipboardList } from "lucide-react";
import { CloudGPTIcon } from "@/components/ui/cloudgpt-icon";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { cn } from "@/lib/utils";
import { usePortals } from "@/hooks/use-portals";
import { useRecentPortals } from "@/hooks/use-recent-portals";
import { addRecentPortal, triggerRecentPortalsUpdate } from "@/lib/storage/recent-portals";
import { useEchoAIStore } from "@/stores/echo-ai-store";
import { EchoChatInterface } from "@/components/echo-ai/echo-chat-interface";
import { ECHO_SIDEBAR_WIDTH } from "@/lib/echo-config";
import { CustomerPortalUserMenu } from "@/components/portal/customer-portal-user-menu";
import { LandingHeroBanner } from "@/components/landing/landing-hero-banner";
import { StandaloneJQLTable } from "@/components/landing/standalone-jql-table";
import type { PortalInfo } from "@/lib/api/portals-client";
import { searchProjects } from "@/lib/api/projects-client";
import { ROUTE_PATHS } from "@/types/router";

const HEADER_HEIGHT = 56;

// Valid tab values from query parameter
type TabValue = "all" | "recent" | "requests" | "approvals" | "assigned";
const VALID_TABS: TabValue[] = ["all", "recent", "requests", "approvals", "assigned"];

// Map query param values to tab values (for URL: ?tab=myrequests, ?tab=myapprovals)
const QUERY_TO_TAB: Record<string, TabValue> = {
  "": "all",
  "recent": "recent",
  "myrequests": "requests",
  "myapprovals": "approvals",
  "assignee": "assigned"
  
};

// Map tab values to query param values
const TAB_TO_QUERY: Record<TabValue, string> = {
  all: "",
  recent: "recent",
  requests: "myrequests",
  approvals: "myapprovals",
  assigned:  "assignee"
};

type BootstrapWindow = typeof window & {
  RAIL_PORTAL_BOOTSTRAP?: {
    resourceBase?: string;
  };
};

export function PortalLandingPageSimple() {
  const location = useLocation();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  
  // Get resource base for asset paths
  const getResourceBase = () => {
    if (typeof window === "undefined") return "";
    const bootstrap = (window as BootstrapWindow).RAIL_PORTAL_BOOTSTRAP;
    return bootstrap?.resourceBase || "";
  };
  
  const resourceBase = getResourceBase();
  const normalizedBase = resourceBase.endsWith("/") ? resourceBase.slice(0, -1) : resourceBase;
  // Construct the full path to the Samsung logo using the Atlassian plugin resource path
  const getSamsungLogoPath = () => {
    if (normalizedBase) {
      // resourceBase is like "/download/resources/com.samsungbuilder.jsm.rail-portal:rail-portal-resources"
      return `${normalizedBase}/SAS_Black_Logo.png`;
    }
    // Fallback path if resourceBase is not available
    return "/download/resources/com.samsungbuilder.jsm.rail-portal:rail-portal-resources/SAS_Black_Logo.png";
  };
  const { data, isLoading } = usePortals();
  const { recentPortals } = useRecentPortals();

  // Derive tab from query parameter
  const getTabFromQuery = useCallback((params: URLSearchParams): TabValue => {
    const tabParam = params.get("tab") || "";
    return QUERY_TO_TAB[tabParam] || "all";
  }, []);

  const [activeTab, setActiveTabState] = useState<TabValue>(() => getTabFromQuery(searchParams));
  const [search, setSearch] = useState("");
  const [activeCategory, setActiveCategory] = useState<string | "all">("all");
  const [isGlobalSearching, setIsGlobalSearching] = useState(false);
  const [globalSearchError, setGlobalSearchError] = useState<string | null>(null);

  // Sync URL query param changes to tab state (e.g., browser back/forward)
  useEffect(() => {
    const newTab = getTabFromQuery(searchParams);
    if (newTab !== activeTab) {
      setActiveTabState(newTab);
    }
  }, [searchParams, activeTab, getTabFromQuery]);

  // Handle tab changes by navigating to the corresponding URL with query param
  const setActiveTab = useCallback((tab: TabValue) => {
    const queryValue = TAB_TO_QUERY[tab];
    const basePath = ROUTE_PATHS.CUSTOMER_PORTAL;
    const targetUrl = queryValue ? `${basePath}?tab=${queryValue}` : basePath;
    navigate(targetUrl);
    setActiveTabState(tab);
  }, [navigate]);

  // Echo AI state
  const echoEnabled = useEchoAIStore((state) => state.enabled);
  const isEchoVisible = useEchoAIStore((state) => state.isVisible);
  const setIsEchoVisible = useEchoAIStore((state) => state.setIsVisible);
  const setEchoEnabled = useEchoAIStore((state) => state.setEnabled);
  const setActiveSpaceKey = useEchoAIStore((state) => state.setActiveSpaceKey);
  const loadEchoConfig = useEchoAIStore((state) => state.loadConfig);

  // Enable global Echo AI on mount with "All Spaces" mode (no specific space)
  useEffect(() => {
    // Explicitly set to null to ensure "All Spaces" mode on landing page
    setActiveSpaceKey(null);
    setEchoEnabled(true);
    setIsEchoVisible(true);
    loadEchoConfig({
      header: "Ask Echo AI",
      subheader: "Get help finding portals or answers to your questions",
      inputPlaceholder: "Ask a question...",
      spaces: [],
      activeSpaceKey: null,
      enabled: true,
    });
  }, [setActiveSpaceKey, setEchoEnabled, setIsEchoVisible, loadEchoConfig]);

  const portals: PortalInfo[] = data?.portals ?? [];

  // Filter portals to only show customer-facing ones
  const visiblePortals = useMemo(() => {
    return portals.filter((portal) => {
      if (portal.jsmPortalId) return true;
      if (portal.isLive) return true;
      return false;
    });
  }, [portals]);

  const categories = useMemo(() => {
    const names = new Set<string>();
    for (const portal of visiblePortals) {
      if (portal.categoryName) names.add(portal.categoryName);
    }
    const sorted = Array.from(names).sort((a, b) => a.localeCompare(b));
    // Move "IT Ticket Service Desks" to the front if it exists
    const itTicketIndex = sorted.findIndex(name => name === "IT Ticket Service Desks");
    if (itTicketIndex > 0) {
      const [itTicket] = sorted.splice(itTicketIndex, 1);
      sorted.unshift(itTicket);
    }
    return sorted;
  }, [visiblePortals]);

  const filteredPortals = useMemo(() => {
    const term = search.trim().toLowerCase();
    return visiblePortals.filter((portal) => {
      if (activeCategory !== "all" && portal.categoryName !== activeCategory) return false;
      if (!term) return true;
      const haystack = `${portal.projectName} ${portal.projectKey}`.toLowerCase();
      return haystack.includes(term);
    });
  }, [activeCategory, visiblePortals, search]);

  const handleSelect = (portal: PortalInfo) => {
    if (!portal.projectKey) return;
    addRecentPortal(portal.projectKey);
    triggerRecentPortalsUpdate();

    if (!portal.isLive && portal.jsmPortalId) {
      window.open(`/plugins/servlet/desk/portal/${portal.jsmPortalId}/`, '_blank', 'noopener,noreferrer');
      return;
    }
    window.location.href = `/plugins/servlet/customer-rail/${portal.projectKey}`;
  };

  const handleGlobalSearch = useCallback(async () => {
    const term = search.trim();
    if (!term) {
      return;
    }

    setGlobalSearchError(null);

    // Prefer in-memory match first for immediate navigation
    const inMemoryMatch = visiblePortals.find((portal) => {
      const haystack = `${portal.projectName} ${portal.projectKey}`.toLowerCase();
      return haystack.includes(term.toLowerCase());
    });

    if (inMemoryMatch) {
      handleSelect(inMemoryMatch);
      return;
    }

    setIsGlobalSearching(true);
    try {
      const result = await searchProjects(term);
      const match = result.projects?.[0];

      if (!match) {
        setGlobalSearchError("No matching portals found.");
        return;
      }

      const matchFromPortals = visiblePortals.find(
        (portal) => portal.projectKey?.toUpperCase() === match.key?.toUpperCase(),
      );

      if (matchFromPortals) {
        handleSelect(matchFromPortals);
        return;
      }

      if (match.portalId) {
        window.open(
          `/plugins/servlet/desk/portal/${match.portalId}/`,
          "_blank",
          "noopener,noreferrer",
        );
        return;
      }

      window.location.href = `/plugins/servlet/customer-rail/${match.key}`;
    } catch (error) {
      console.error("Global portal search failed", error);
      setGlobalSearchError("Search failed. Please try again.");
    } finally {
      setIsGlobalSearching(false);
    }
  }, [handleSelect, search, visiblePortals]);

  return (
    <div className="min-h-screen flex flex-col bg-background">
      {/* Header */}
      <header className="border-b bg-background sticky top-0 z-50" style={{ height: HEADER_HEIGHT }}>
        <div className="h-full px-6 flex items-center justify-between">
          <div className="flex items-center">
            <img
              src={getSamsungLogoPath()}
              alt="Samsung"
              className="h-6 object-contain"
            />
          </div>
          <div className="flex items-center gap-3">
            {echoEnabled && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setIsEchoVisible(!isEchoVisible)}
                className="gap-2 cursor-pointer"
              >
                <span className="flex items-center gap-2 text-primary">
                  <CloudGPTIcon size={16} className="text-primary" />
                  <span className="hidden sm:inline">
                    {isEchoVisible ? "Hide Echo" : "Ask Echo"}
                  </span>
                </span>
              </Button>
            )}
            <CustomerPortalUserMenu />
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main
        className="flex-1 overflow-y-auto"
        style={echoEnabled && isEchoVisible ? { paddingRight: ECHO_SIDEBAR_WIDTH } : undefined}
      >
        {/* Hero Banner */}
        <LandingHeroBanner />

        <div className="px-6 py-8">
          <div className="max-w-5xl mx-auto space-y-6">
            {/* Tabs */}
            <Tabs value={activeTab} onValueChange={(val) => setActiveTab(val as typeof activeTab)}>
              <TabsList>
                <TabsTrigger value="all" className="cursor-pointer">
                  <FolderTree className="h-3.5 w-3.5 mr-1.5" />
                  All Portals
                </TabsTrigger>
                <TabsTrigger value="recent" className="cursor-pointer">
                  <Clock className="h-3.5 w-3.5 mr-1.5" />
                  Recently Visited Portals
                </TabsTrigger>
                <TabsTrigger value="requests" className="cursor-pointer">
                  <ListTodo className="h-3.5 w-3.5 mr-1.5" />
                  My Requests
                </TabsTrigger>
                <TabsTrigger value="approvals" className="cursor-pointer">
                  <CheckCircle className="h-3.5 w-3.5 mr-1.5" />
                  My Approvals
                </TabsTrigger>
                <TabsTrigger value="assigned" className="cursor-pointer">
                  <ClipboardList className="h-3.5 w-3.5 mr-1.5" />
                  Assigned to Me
                  </TabsTrigger>
              </TabsList>

              {/* Recently Visited Portals Tab */}
              <TabsContent value="recent" className="mt-4">
                {isLoading && <p className="text-sm text-muted-foreground">Loading portals...</p>}
                {!isLoading && recentPortals.length === 0 && (
                  <EmptyState icon={Clock} title="No recently visited portals" subtitle="Visit a portal to see it here" />
                )}
                <PortalGrid portals={recentPortals} onSelect={handleSelect} />
              </TabsContent>

              {/* All Portals Tab */}
              <TabsContent value="all" className="mt-4">
                <div className="flex gap-6">
                  {/* Sidebar Categories */}
                  <div className="w-48 shrink-0 space-y-1">
                    <CategoryButton label="All portals" isActive={activeCategory === "all"} onClick={() => setActiveCategory("all")} />
                    {categories.map((name) => (
                      <CategoryButton key={name} label={name} isActive={activeCategory === name} onClick={() => setActiveCategory(name)} />
                    ))}
                  </div>
                  {/* Portal List */}
                  <div className="flex-1">
                    <div className="mb-4">
                      <div className="relative max-w-sm">
                        <Search className="pointer-events-none absolute left-2.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                        <Input
                          value={search}
                          onChange={(e) => setSearch(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === "Enter") {
                              e.preventDefault();
                              void handleGlobalSearch();
                            }
                          }}
                          placeholder="Search portals..."
                          className="pl-8 pr-24"
                        />
                        <Button
                          size="sm"
                          variant="secondary"
                          className="absolute right-1 top-1/2 -translate-y-1/2 h-8 px-3 cursor-pointer"
                          onClick={() => void handleGlobalSearch()}
                          disabled={isGlobalSearching}
                        >
                          {isGlobalSearching ? (
                            <Loader2 className="h-4 w-4 animate-spin" />
                          ) : (
                            "Search"
                          )}
                        </Button>
                      </div>
                      {globalSearchError && (
                        <p className="mt-1 text-xs text-destructive">{globalSearchError}</p>
                      )}
                    </div>
                    {isLoading && <p className="text-sm text-muted-foreground">Loading portals...</p>}
                    {!isLoading && filteredPortals.length === 0 && (
                      <EmptyState icon={FolderTree} title="No portals found" subtitle="Try a different search or filter" />
                    )}
                    <PortalGrid portals={filteredPortals} onSelect={handleSelect} />
                  </div>
                </div>
              </TabsContent>

              {/* My Requests Tab */}
              <TabsContent value="requests" className="mt-4">
                <StandaloneJQLTable
                  jqlQuery="reporter = currentUser() ORDER BY created DESC"
                  title="My Requests"
                  subtitle="Issues you have submitted"
                  columns={[
                    { id: "key", name: "Key", isCustom: false },
                    { id: "summary", name: "Summary", isCustom: false },
                    { id: "status", name: "Status", isCustom: false },
                    { id: "priority", name: "Priority", isCustom: false },
                    { id: "created", name: "Created", isCustom: false },
                  ]}
                  pageSize={10}
                  showSearch={true}
                  showFilter={true}
                />
              </TabsContent>

              {/* My Approvals Tab */}
              <TabsContent value="approvals" className="mt-4">
                <StandaloneJQLTable
                  jqlQuery="Approvals = myPending()"
                  title="My Approvals"
                  subtitle="Issues awaiting your approval"
                  columns={[
                    { id: "key", name: "Key", isCustom: false },
                    { id: "summary", name: "Summary", isCustom: false },
                    { id: "status", name: "Status", isCustom: false },
                    { id: "priority", name: "Priority", isCustom: false },
                    { id: "created", name: "Created", isCustom: false },
                  ]}
                  pageSize={10}
                  showSearch={true}
                  showFilter={false}
                />
              </TabsContent>
              {/* Assigned to Me Tab */}
              <TabsContent value="assigned" className="mt-4">
                <StandaloneJQLTable
                  jqlQuery="assignee = currentUser() ORDER BY created DESC"
                  title="Assigned to Me"
                  subtitle="Issues assigned to you"
                  columns={[
                    { id: "key", name: "Key", isCustom: false },
                    { id: "summary", name: "Summary", isCustom: false },
                    { id: "status", name: "Status", isCustom: false },
                    { id: "priority", name: "Priority", isCustom: false },
                    { id: "created", name: "Created", isCustom: false },
                  ]}
                  pageSize={10}
                  showSearch={true}
                  showFilter={false}
                />
              </TabsContent>
            </Tabs>
          </div>
        </div>
      </main>

      {/* Echo AI Sidebar */}
      <AnimatePresence mode="wait">
        {echoEnabled && isEchoVisible && (
          <motion.div
            key="echo-ai-landing"
            initial={{ x: ECHO_SIDEBAR_WIDTH, opacity: 0 }}
            animate={{ x: 0, opacity: 1 }}
            exit={{ x: ECHO_SIDEBAR_WIDTH, opacity: 0 }}
            transition={{ type: "spring", stiffness: 300, damping: 30, mass: 0.8 }}
            className="fixed right-0 bg-background border-l z-30"
            style={{ width: ECHO_SIDEBAR_WIDTH, top: HEADER_HEIGHT, height: `calc(100vh - ${HEADER_HEIGHT}px)` }}
          >
            <EchoChatInterface mode="customer" />
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}

// Helper Components
function EmptyState({ icon: Icon, title, subtitle }: { icon: any; title: string; subtitle?: string }) {
  return (
    <div className="flex flex-col items-center justify-center py-12 text-center">
      <Icon className="h-10 w-10 text-muted-foreground/30 mb-3" />
      <p className="text-sm font-medium text-muted-foreground">{title}</p>
      {subtitle && <p className="text-xs text-muted-foreground mt-1">{subtitle}</p>}
    </div>
  );
}

function CategoryButton({ label, isActive, onClick }: { label: string; isActive: boolean; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        "w-full rounded-md px-3 py-2 text-left text-sm cursor-pointer hover:bg-accent",
        isActive && "bg-accent text-accent-foreground font-medium"
      )}
    >
      {label}
    </button>
  );
}

function PortalGrid({ portals, onSelect }: { portals: PortalInfo[]; onSelect: (p: PortalInfo) => void }) {
  if (portals.length === 0) return null;
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
      {portals.map((portal) => (
        <PortalCard key={portal.projectKey} portal={portal} onSelect={onSelect} />
      ))}
    </div>
  );
}

function PortalCard({ portal, onSelect }: { portal: PortalInfo; onSelect: (p: PortalInfo) => void }) {
  return (
    <button
      type="button"
      onClick={() => onSelect(portal)}
      className="flex flex-col items-start gap-1.5 rounded-lg border bg-card p-4 text-left cursor-pointer hover:bg-accent transition-colors w-full overflow-hidden"
    >
      <div className="flex items-center gap-2 w-full min-w-0">
        <span className="font-medium text-foreground truncate flex-1 min-w-0">{portal.projectName}</span>
        {portal.isLive && (
          <span className="shrink-0 rounded-full bg-emerald-100 px-1.5 py-px text-[10px] font-semibold text-emerald-700">Live</span>
        )}
        {!portal.isLive && portal.jsmPortalId && (
          <span className="shrink-0 rounded-full bg-blue-100 px-1.5 py-px text-[10px] font-semibold text-blue-700">JSM</span>
        )}
      </div>
      <p className="text-xs text-muted-foreground">{portal.projectKey}</p>
    </button>
  );
}
