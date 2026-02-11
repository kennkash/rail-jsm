[INFO] Finished at: 2026-02-11T17:54:55-06:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin:3.11.0:compile (default-compile) on project rail-portal-plugin: Compilation failure: Compilation failure: 
[ERROR] /mnt/k.kashmiry/git/rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java:[1112,75] cannot find symbol
[ERROR]   symbol:   method getGroupedRequestTypes(java.lang.String)
[ERROR]   location: variable requestTypeService of type com.samsungbuilder.jsm.service.PortalRequestTypeService
[ERROR] /mnt/k.kashmiry/git/rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java:[1133,60] cannot find symbol
[ERROR]   symbol:   method getRequestTypeById(java.lang.String)
[ERROR]   location: variable requestTypeService of type com.samsungbuilder.jsm.service.PortalRequestTypeService
[ERROR] /mnt/k.kashmiry/git/rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java:[1164,62] cannot find symbol
[ERROR]   symbol:   method searchRequestTypes(java.lang.String,java.lang.String)
[ERROR]   location: variable requestTypeService of type com.samsungbuilder.jsm.service.PortalRequestTypeService
[ERROR] /mnt/k.kashmiry/git/rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java:[1199,42] cannot find symbol
[ERROR]   symbol:   class GlobalRequestTypeSearchResult
[ERROR]   location: class com.samsungbuilder.jsm.service.PortalRequestTypeService
[ERROR] /mnt/k.kashmiry/git/rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java:[1200,39] cannot find symbol
[ERROR]   symbol:   method searchAllRequestTypes(java.lang.String,int)
[ERROR]   location: variable requestTypeService of type com.samsungbuilder.jsm.service.PortalRequestTypeService
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
ERROR: Backend build failed!




// rail-at-sas/frontend/components/portal-builder/form-components/portal-request-types.tsx
'use client'

import { FormComponentModel } from "@/models/FormComponent";
import { UseFormReturn, ControllerRenderProps, FieldValues } from "react-hook-form";
import { ReactCode, Viewports } from "@/types/portal-builder.types";
import { Card, CardHeader, CardTitle, CardDescription, CardContent } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Checkbox } from "@/components/ui/checkbox";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { ToggleGroup, ToggleGroupItem } from "@/components/ui/toggle-group";
import { cn } from "@/lib/utils";
import { usePortalBuilderStore } from "@/stores/portal-builder-store";
import { useShallow } from "zustand/react/shallow";
import { useRequestTypes } from "@/hooks/use-request-types";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Loader2 } from "lucide-react";
import { CloudGPTIcon } from "@/components/ui/cloudgpt-icon";
import { GradientText } from "@/components/ui/gradient-text";
import { generateRequestTypesSubtitle } from "@/lib/cloudgpt-api";
import { Textarea } from "@/components/ui/textarea";
import { ROUTE_PATHS, buildRoutePath, buildRequestTypeUrl } from "@/types/router";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

type BootstrapWindow = typeof window & {
  RAIL_PORTAL_BOOTSTRAP?: {
    baseUrl?: string
  }
}

const getBootstrapBaseUrl = () => {
  if (typeof window === "undefined") {
    return ""
  }
  const bootstrap = (window as BootstrapWindow).RAIL_PORTAL_BOOTSTRAP
  return (bootstrap?.baseUrl ?? window.location.origin).replace(/\/$/, "")
}

type RequestTypeOption = {
  id: string;
  name: string;
  description?: string;
  iconUrl?: string;
  color?: string;
  group?: string;
  groups?: string[];
  groupIds?: string[];
  displayOrder?: number;
  groupOrderMap?: Record<string, number>;
};

type RequestTypesApiResponse = {
  requestTypes?: RequestTypeOption[];
  groups?: Array<{
    id: string;
    name: string;
    displayOrder?: number;
  }>;
};

type RequestTypeRoutingOptions = {
  routeProjectKey?: string;
  routeRequestTypeId?: string | null;
  onRouteChange?: (path: string, replace?: boolean) => void;
  currentPath?: string;
};

const REQUEST_DIALOG_IFRAME_STYLE_ID = "rail-request-dialog-iframe-style";
const REQUEST_DIALOG_IFRAME_STYLES = `
  header#header {
    display: none !important;
  }

  .cv-bg-image {
    display: none !important;
  }

  #content-wrapper {
    margin: 0px !important;
    width: 100% !important;
    padding: 20px 75px 0px !important;
  }

  #footer {
    display: none !important;
  }
`;

/**
 * Default sample request types with grouping
 * Groups help organize request types into logical categories
 */
const defaultRequestTypes: RequestTypeOption[] = [
  // Support Group
  {
    id: "help",
    name: "Get Help",
    description: "Ask a question or get support",
    group: "Support",
    groups: ["Support"],
  },
  {
    id: "incident",
    name: "Report an Incident",
    description: "Report a problem or issue",
    group: "Support",
    groups: ["Support"],
  },
  {
    id: "question",
    name: "Ask a Question",
    description: "General questions and inquiries",
    group: "Support",
    groups: ["Support"],
  },

  // Service Requests Group
  {
    id: "service-request",
    name: "Service Request",
    description: "Request a service or item",
    group: "Services",
    groups: ["Services"],
  },
  {
    id: "access-request",
    name: "Access Request",
    description: "Request access to systems or resources",
    group: "Services",
    groups: ["Services"],
  },
  {
    id: "equipment",
    name: "Equipment Request",
    description: "Request equipment or supplies",
    group: "Services",
    groups: ["Services"],
  },

  // Change & Improvement Group
  {
    id: "change",
    name: "Request a Change",
    description: "Propose a change or improvement",
    group: "Changes",
    groups: ["Changes"],
  },
  {
    id: "feature",
    name: "Feature Request",
    description: "Suggest a new feature",
    group: "Changes",
    groups: ["Changes"],
  },
];

/**
 * PortalRequestTypes Widget
 * Displays Jira Service Management request types in a grid or list layout
 *
 * Features:
 * - Grid/List layout toggle
 * - Grouping by category
 * - 1-4 column grid support
 * - Sample data (ready for JSM integration)
 *
 * Widget Rules:
 * - Reusable (can have multiple on a portal)
 * - Full-width widget
 * - Uses sample data until JSM endpoint connected
 */
export function PortalRequestTypes(
  component: FormComponentModel,
  form: UseFormReturn<FieldValues, undefined>,
  field: ControllerRenderProps,
  viewport?: Viewports,
  routingOptions?: RequestTypeRoutingOptions,
) {
  const title = component.getField("content", viewport) || "How can we help you?";
  const subtitle = component.getField("description", viewport) || "";
  const variant =
    (component.getField("properties.variant", viewport) as string | undefined) || "cards";
  const layout = component.getField("properties.layout", viewport) || "grid";
  const columns = component.getField("properties.columns", viewport) || "2";
  const showGroups =
    (component.getField("properties.showGroups", viewport) ?? "yes") !== "no";
  const groupLayout =
    (component.getField("properties.groupLayout", viewport) as string | undefined) ||
    "top-tabs";
  const configuredRequestTypes =
    (component.getField("properties.requestTypes", viewport) as
      | RequestTypeOption[]
      | null
      | undefined) || defaultRequestTypes;
  const portalProjectKey = usePortalBuilderStore((state) => state.projectKey);
  const portalIdFromStore = usePortalBuilderStore((state) => state.portalId);
  const serviceDeskIdFromStore = usePortalBuilderStore((state) => state.serviceDeskId);
  const isLiveFromStore = usePortalBuilderStore((state) => state.isLive);
  const mode = usePortalBuilderStore((state) => state.mode);
  const isCustomerPortal = mode === "preview";
  const configuredProjectKey = component.getField("properties.projectKey", viewport) as string | undefined;
  const resolvedApiProjectKey = portalProjectKey || configuredProjectKey || null;
  const effectiveProjectKey = (resolvedApiProjectKey || "PROJECT").toUpperCase();
  const fallbackPortalId = resolvedApiProjectKey ? `${resolvedApiProjectKey.toLowerCase()}-portal` : "default";
  const resolvedPortalId = portalIdFromStore || fallbackPortalId;
  const resolvedServiceDeskId = serviceDeskIdFromStore || null;
  const { data, isLoading } = useRequestTypes(resolvedApiProjectKey) as {
    data?: RequestTypesApiResponse;
    isLoading: boolean;
  };
  const [jsmIconUrls, setJsmIconUrls] = useState<Record<string, string> | null>(null);

  useEffect(() => {
    if (!resolvedServiceDeskId) {
      setJsmIconUrls(null);
      return;
    }

    let cancelled = false;

    const fetchIcons = async () => {
      try {
        const base = getBootstrapBaseUrl();
        if (!base) {
          return;
        }

        const response = await fetch(
          `${base}/rest/servicedeskapi/servicedesk/${resolvedServiceDeskId}/requesttype?limit=100`,
          { credentials: 'same-origin' },
        );

        if (!response.ok) {
          return;
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
            mapping[value.id] = url;
          }
        }

        if (!cancelled) {
          setJsmIconUrls(mapping);
        }
      } catch (error) {
        // Silently fail - icons are optional
      }
    };

    fetchIcons();

    return () => {
      cancelled = true;
    };
  }, [resolvedServiceDeskId]);

  const [selectedRequestType, setSelectedRequestType] = useState<RequestTypeOption | null>(null);
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [iframeUrl, setIframeUrl] = useState<string | null>(null);
  const [iframeStatus, setIframeStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const [lastKnownHref, setLastKnownHref] = useState<string | null>(null);
  const [createdIssueKey, setCreatedIssueKey] = useState<string | null>(null);
  const [postSubmitUrl, setPostSubmitUrl] = useState<string | null>(null);
  const [lastSubmittedRequest, setLastSubmittedRequest] = useState<{ name: string; description?: string | null } | null>(null);
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const routeProjectKey = routingOptions?.routeProjectKey || resolvedApiProjectKey || portalProjectKey || undefined;
  const routeRequestTypeId = routingOptions?.routeRequestTypeId;
  const onRouteChange = routingOptions?.onRouteChange;
  
  const apiRequestTypes = useMemo<RequestTypeOption[] | null>(() => {
    if (!data?.requestTypes?.length) {
      return null;
    }

    return data.requestTypes.map((type) => {
      const iconUrlFromJsm = jsmIconUrls?.[type.id];

      return {
        id: type.id,
        name: type.name,
        description: type.description,
        iconUrl: iconUrlFromJsm || type.iconUrl,
        group: type.group || "Other",
        groups: type.groups && type.groups.length > 0 ? type.groups : undefined,
        groupIds: type.groupIds,
        displayOrder: type.displayOrder ?? 999,
        groupOrderMap: type.groupOrderMap || {},
      };
    });
  }, [data, jsmIconUrls]);
  
  const requestTypes = apiRequestTypes ?? configuredRequestTypes;
  const apiGroupNames = data?.groups?.map((group) => group.name) ?? [];
  const enableInteractivePreview = mode === "editor-preview" || mode === "preview";
  const injectIframeBrandingStyles = useCallback(() => {
    const frame = iframeRef.current;
    if (!frame) {
      return;
    }
    try {
      const doc = frame.contentDocument || frame.contentWindow?.document;
      if (!doc) {
        return;
      }
      const targetRoot = doc.head || doc.body;
      if (!targetRoot) {
        return;
      }
      let styleEl = doc.getElementById(REQUEST_DIALOG_IFRAME_STYLE_ID) as HTMLStyleElement | null;
      if (!styleEl || styleEl.tagName !== "STYLE") {
        if (styleEl) {
          styleEl.remove();
        }
        styleEl = doc.createElement("style");
        styleEl.id = REQUEST_DIALOG_IFRAME_STYLE_ID;
        styleEl.type = "text/css";
        targetRoot.appendChild(styleEl);
      }
      styleEl.textContent = REQUEST_DIALOG_IFRAME_STYLES;
    } catch (error) {
      // Silently fail - style injection is best-effort
    }
  }, []);

  const handleSubmissionSuccess = useCallback(
    (
      href: string | null,
      issueKey: string | null,
      requestDetails?: { name: string; description?: string | null },
    ) => {
      setCreatedIssueKey(issueKey);
      setPostSubmitUrl(href ?? null);
      setIframeUrl(null);
      setIframeStatus("ready");
      setLastSubmittedRequest(requestDetails ?? null);
    },
    [],
  );

  const buildRequestUrl = useCallback((requestTypeId: string) => {
    if (!enableInteractivePreview) {
      return null;
    }
    const base = getBootstrapBaseUrl();
    if (!base) {
      return null;
    }
    const portalIdentifier = resolvedServiceDeskId || resolvedPortalId;
    return `${base}/servicedesk/customer/portal/${portalIdentifier}/create/${requestTypeId}`;
  }, [enableInteractivePreview, resolvedPortalId]);

  const handleRequestTypeClick = useCallback((requestType: RequestTypeOption) => {
    if (!enableInteractivePreview) {
      return;
    }
    setIframeStatus("loading");
    setCreatedIssueKey(null);
    setPostSubmitUrl(null);
    if (onRouteChange && routeProjectKey) {
      // When on customer portal (preview mode), always use RAIL URL since customer-rail is a Live portal
      // When in editor-preview mode, use the store's isLive status
      const effectiveIsLive = isCustomerPortal || isLiveFromStore;
      const targetPath = buildRequestTypeUrl(
        effectiveIsLive,
        routeProjectKey,
        requestType.id,
        resolvedServiceDeskId ?? undefined
      );
      onRouteChange(targetPath, false);
    }
    const requestUrl = buildRequestUrl(requestType.id);
    setSelectedRequestType(requestType);
    setIframeUrl(requestUrl);
    setIsDialogOpen(Boolean(requestUrl));
  }, [buildRequestUrl, enableInteractivePreview, isCustomerPortal, isLiveFromStore, onRouteChange, resolvedServiceDeskId, routeProjectKey]);

  // Auto-open dialog when deep-linked via requesttype route
  useEffect(() => {
    if (!enableInteractivePreview || !routeRequestTypeId || !requestTypes?.length) {
      return;
    }
    const match = requestTypes.find((rt) => rt.id === routeRequestTypeId);
    if (!match) {
      return;
    }
    const url = buildRequestUrl(match.id);
    setSelectedRequestType(match);
    setIframeUrl(url);
    setIsDialogOpen(Boolean(url));
    setIframeStatus(url ? "loading" : "idle");
    setCreatedIssueKey(null);
    setPostSubmitUrl(null);
  }, [enableInteractivePreview, routeRequestTypeId, requestTypes, buildRequestUrl]);

  const parseSuccessFromHref = (href: string | null) => {
    if (!href) return { issueKey: null, success: false };
    try {
      const url = new URL(href, window.location.origin);
      // Success URLs typically look like /servicedesk/customer/portal/{portalId}/{ISSUEKEY}
      const pathParts = url.pathname.split("/").filter(Boolean);
      const issueKeyCandidate = pathParts[pathParts.length - 1];
      const isCreatePath = url.pathname.includes("/create/");
      const looksLikeIssueKey = issueKeyCandidate && /[A-Z0-9]+-\d+/.test(issueKeyCandidate);
      return {
        issueKey: looksLikeIssueKey ? issueKeyCandidate : null,
        success: !isCreatePath && looksLikeIssueKey,
      };
    } catch (e) {
      return { issueKey: null, success: false };
    }
  };

  // Poll the iframe location for success redirect
  useEffect(() => {
    if (!isDialogOpen || iframeStatus === "error") return;
    const interval = window.setInterval(() => {
      const frame = iframeRef.current;
      if (!frame?.contentWindow) return;
      try {
        const href = frame.contentWindow.location?.href;
        if (href && href !== lastKnownHref) {
          setLastKnownHref(href);
          const parsed = parseSuccessFromHref(href);
          if (parsed.success) {
            const requestDetails = selectedRequestType
              ? { name: selectedRequestType.name, description: selectedRequestType.description }
              : undefined;
            handleSubmissionSuccess(href, parsed.issueKey, requestDetails);
          }
        }
      } catch (e) {
        // Cross-origin would land here; for our same-origin iframe this should not trigger
      }
    }, 1000);
    return () => window.clearInterval(interval);
  }, [handleSubmissionSuccess, isDialogOpen, iframeStatus, lastKnownHref, selectedRequestType]);

  const resetDialogState = useCallback(() => {
    setIframeStatus("idle");
    setCreatedIssueKey(null);
    setPostSubmitUrl(null);
    setLastKnownHref(null);
    setLastSubmittedRequest(null);
  }, []);

  // Group request types by category and SORT them
  const groupedSections = useMemo(() => {
    if (!requestTypes.length) return [];

    // 1. Group items by their group names
    const sections: Record<string, RequestTypeOption[]> = {};
    requestTypes.forEach((type) => {
      const targetGroups = type.groups?.length ? type.groups : [type.group || "Other"];
      targetGroups.forEach(groupName => {
        if (!sections[groupName]) sections[groupName] = [];
        sections[groupName].push(type);
      });
    });

    // 2. Map Group Names to IDs to lookup sort order
    const apiGroups = data?.groups || [];
    const groupNameToId = new Map(apiGroups.map(g => [g.name, g.id]));

    // 3. Sort Group Tabs based on Backend Order
    // The backend returns groups sorted by their discovery/settings order
    const sortedGroupNames = apiGroups.length > 0 
      ? apiGroups.map(g => g.name) 
      : Object.keys(sections).sort();

    // 4. Sort Items Within Each Group
    return sortedGroupNames
      .filter(name => sections[name])
      .map(name => {
        const groupId = groupNameToId.get(name);
        
        const sortedItems = [...sections[name]].sort((a, b) => {
          // Look up specific order for this group ID from the groupOrderMap
          // If a request type is in multiple groups, this map ensures it has the 
          // correct relative position for *this specific group context*
          const orderA = groupId ? (a.groupOrderMap?.[groupId] ?? 999) : (a.displayOrder ?? 999);
          const orderB = groupId ? (b.groupOrderMap?.[groupId] ?? 999) : (b.displayOrder ?? 999);
          
          if (orderA !== orderB) return orderA - orderB;
          return a.name.localeCompare(b.name);
        });
        return { name, types: sortedItems };
      });
  }, [data?.groups, requestTypes]);

  const [activeGroup, setActiveGroup] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (!isCustomerPortal) {
      return;
    }

    if (!groupedSections.length) {
      return;
    }

    setActiveGroup((current) => {
      const groupNames = groupedSections.map((section) => section.name);
      if (current && groupNames.includes(current)) {
        return current;
      }
      return groupedSections[0]?.name;
    });
  }, [isCustomerPortal, groupedSections]);

  const renderTypeAvatar = (
    type: RequestTypeOption,
    className?: string,
    sizeClass = "h-8 w-8",
  ) => {
    const initial = type.name?.charAt(0)?.toUpperCase() ?? "?";

    return (
      <Avatar className={cn(sizeClass, className, "!rounded-none")}>
        {type.iconUrl ? (
          <AvatarImage src={type.iconUrl} alt={`${type.name} icon`} />
        ) : null}
        <AvatarFallback className="text-xs font-medium !rounded-none">
          {initial}
        </AvatarFallback>
      </Avatar>
    );
  };

  const RequestTypeCard = ({ type, index }: { type: RequestTypeOption; index: number }) => {
    return (
      <Card
        key={type.id || index}
        data-request-type={type.id}
        className={cn(
          "h-full cursor-pointer transition-all duration-200 hover:border-border hover:shadow-md"
        )}
        onClick={() => {
          handleRequestTypeClick(type);
        }}
      >
        <CardHeader className="space-y-2">
          <div className="flex items-center gap-4">
            <div className="mt-1">
              {renderTypeAvatar(type, undefined, "h-7 w-7")}
            </div>
            <CardTitle className="text-lg font-semibold text-gray-900 text-left">
              {type.name}
            </CardTitle>
          </div>
          {type.description ? (
            <CardDescription className="text-sm text-muted-foreground mt-0.5 text-left">
              {type.description}
            </CardDescription>
          ) : null}
        </CardHeader>
      </Card>
    );
  };

  const renderRequestTypes = (types: RequestTypeOption[]) => {
    if (!types?.length) return null;

    if (variant === "compact-list") {
      return (
        <div className="divide-y rounded-md border bg-background">
          {types.map((type) => (
            <button
              key={type.id}
              type="button"
              className="flex w-full items-start gap-3 px-3 py-3 text-left text-sm hover:bg-muted cursor-pointer"
              onClick={() => handleRequestTypeClick(type)}
            >
              <div className="mt-0.5">{renderTypeAvatar(type)}</div>
              <div className="flex-1">
                <div className="font-medium text-foreground">{type.name}</div>
                {type.description && (
                  <p className="text-xs text-muted-foreground line-clamp-2">
                    {type.description}
                  </p>
                )}
              </div>
            </button>
          ))}
        </div>
      );
    }

    if (variant === "accordion") {
      return (
        <Accordion
          type="single"
          collapsible
          className="w-full rounded-md border bg-background"
        >
          {types.map((type) => (
            <AccordionItem key={type.id} value={type.id}>
              <AccordionTrigger className="px-3 py-2 cursor-pointer">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5">{renderTypeAvatar(type)}</div>
                  <div className="text-left">
                    <div className="font-medium text-foreground">{type.name}</div>
                    {type.description && (
                      <p className="text-xs text-muted-foreground line-clamp-2">
                        {type.description}
                      </p>
                    )}
                  </div>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-3 pb-3">
                <Button
                  type="button"
                  size="sm"
                  className="mt-1 cursor-pointer"
                  onClick={() => handleRequestTypeClick(type)}
                >
                  Open this request type
                </Button>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      );
    }

    const columnCount = Math.min(Math.max(Number(columns) || 2, 1), 4);

    return (
      <div
        className={cn(
          layout === "grid" ? "grid gap-4" : "flex flex-col gap-4"
        )}
        style={
          layout === "grid"
            ? {
                gridTemplateColumns: `repeat(${columnCount}, minmax(${MIN_CARD_WIDTH}px, 1fr))`,
              }
            : undefined
        }
      >
        {types.map((type, index) => (
          <RequestTypeCard key={type.id || index} type={type} index={index} />
        ))}
      </div>
    );
  };

  // Render sectioned layout - stacked sections without tabs
  const renderSectionedLayout = () => (
    <div className="space-y-8">
      {groupedSections.map(({ name, types }) => (
        <div key={name} className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-lg font-semibold text-foreground">{name}</h3>
            <Badge variant="outline" className="rounded-full">
              {types.length} {types.length === 1 ? 'type' : 'types'}
            </Badge>
          </div>
          <Separator className="my-2" />
          {renderRequestTypes(types)}
        </div>
      ))}
    </div>
  );

  const isCompactDialog = !iframeUrl || iframeStatus !== "ready";
  const dialogMaxHeightClass = isCompactDialog ? "max-h-[60vh]" : "max-h-[90vh]";
  const loadingLabel = selectedRequestType?.name ?? "request";
  const frameContainerClass = iframeStatus === "ready"
    ? "h-[750px]"
    : "flex-1 min-h-[320px] sm:min-h-[380px]";

  return (
    <>
      <div className="w-full space-y-6">
        {/* Header */}
        {(title || subtitle) && (
          <div className="space-y-2">
            {title && (
              <h2 className="text-2xl font-bold text-foreground">{title}</h2>
            )}
            {subtitle && (
              <p className="text-muted-foreground">{subtitle}</p>
            )}
          </div>
        )}

        {isLoading && (
          <div className="space-y-4">
            <Skeleton className="h-10 w-full rounded-xl" />
            <div className="grid gap-4 grid-cols-1 md:grid-cols-2">
              <Skeleton className="h-24 rounded-xl" />
              <Skeleton className="h-24 rounded-xl" />
            </div>
          </div>
        )}

        {/* Request Types Display with Grouping */}
        {showGroups && groupedSections.length > 1 ? (
          groupLayout === "top-tabs" ? (
            // Top tabs layout - horizontal tabs at top
            <div className="flex w-full flex-col gap-6">
              <Tabs
                value={isCustomerPortal ? activeGroup ?? groupedSections[0]?.name ?? "" : undefined}
                defaultValue={!isCustomerPortal ? groupedSections[0]?.name ?? "" : undefined}
                onValueChange={isCustomerPortal ? setActiveGroup : undefined}
                className="w-full"
              >
                <TabsList className="flex w-fit flex-wrap justify-start gap-2">
                  {groupedSections.map(({ name, types }) => (
                    <TabsTrigger key={name} value={name} className="px-3 py-1.5 text-sm cursor-pointer">
                      <span className="truncate">{name}</span>
                      <Badge variant="secondary" className="ml-2 rounded-full px-2 text-[11px]">
                        {types.length}
                      </Badge>
                    </TabsTrigger>
                  ))}
                </TabsList>

                {groupedSections.map(({ name, types }) => (
                  <TabsContent key={name} value={name} className="space-y-4">
                    <Card className="border-0 bg-transparent shadow-none">
                      <CardContent className="p-0">{renderRequestTypes(types)}</CardContent>
                    </Card>
                  </TabsContent>
                ))}
              </Tabs>
            </div>
          ) : (
            // Sectioned list layout - stacked sections
            renderSectionedLayout()
          )
        ) : showGroups ? (
          // Single group - always use sectioned display
          renderSectionedLayout()
        ) : (
          // Ungrouped display - flat list
          <>{renderRequestTypes(requestTypes)}</>
        )}

      </div>

      {enableInteractivePreview && (
        <Dialog
          open={isDialogOpen}
          onOpenChange={(open) => {
            setIsDialogOpen(open);
            if (!open) {
              resetDialogState();
            }
            if (!open && onRouteChange && routeProjectKey) {
              const basePath = buildRoutePath(ROUTE_PATHS.CUSTOMER_PORTAL_PROJECT, {
                projectKey: routeProjectKey,
              });
              onRouteChange(basePath, true);
            }
          }}
        >
          <DialogContent className={cn("sm:max-w-[800px] flex flex-col gap-4", dialogMaxHeightClass)}>

            {postSubmitUrl ? (
              <div className="space-y-4">
                <p className="text-sm text-muted-foreground">
                  Your request {createdIssueKey ? `(${createdIssueKey}) ` : ""}was created successfully.
                </p>
                {lastSubmittedRequest && (
                  <div className="rounded-md border bg-muted/40 p-4 space-y-2">
                    <p className="text-sm font-semibold text-foreground">
                      {lastSubmittedRequest.name}
                    </p>
                    {lastSubmittedRequest.description && (
                      <p className="text-sm text-muted-foreground">
                        {lastSubmittedRequest.description}
                      </p>
                    )}
                  </div>
                )}
                <div className="grid gap-3 sm:grid-cols-2">
                  <Button
                    variant="default"
                    className="w-full cursor-pointer"
                    onClick={() => window.open(postSubmitUrl, "_blank", "noopener")}
                  >
                    View request
                  </Button>
                  <Button
                    variant="secondary"
                    className="w-full cursor-pointer"
                    onClick={() => {
                      window.location.reload();
                    }}
                  >
                    Submit another {selectedRequestType?.name ?? "request"}
                  </Button>
                </div>
              </div>
            ) : iframeUrl ? (
              <div className={cn(
                "relative rounded-md border overflow-hidden w-full",
                frameContainerClass,
                iframeStatus !== "ready" && "max-h-[60vh]"
              )}>
                {iframeStatus === "loading" && (
                  <div className="absolute inset-0 z-10 flex flex-col items-center justify-center gap-3 bg-background/90 backdrop-blur-sm">
                    <Loader2 className="h-8 w-8 animate-spin text-primary" aria-hidden="true" />
                    <p className="text-sm text-muted-foreground">
                      Loading {loadingLabel}...
                    </p>
                  </div>
                )}
                <iframe
                  key={iframeUrl}
                  ref={iframeRef}
                  title="Jira Service Management Request Form"
                  src={iframeUrl}
                  className="h-full w-full bg-white"
                  onLoad={() => {
                    setIframeStatus("ready");
                    injectIframeBrandingStyles();
                    const href = iframeRef.current?.contentWindow?.location?.href || null;
                    setLastKnownHref(href);
                    const parsed = parseSuccessFromHref(href);
                    if (parsed.success) {
                      const requestDetails = selectedRequestType
                        ? { name: selectedRequestType.name, description: selectedRequestType.description }
                        : undefined;
                      handleSubmissionSuccess(href, parsed.issueKey, requestDetails);
                    }
                  }}
                  onError={() => {
                    setIframeStatus("error");
                  }}
                />
              </div>
            ) : (
              <p className="text-sm text-muted-foreground">
                Unable to open the request form. Please try again later.
              </p>
            )}

            {iframeStatus === "error" && (
              <div className="mt-3 rounded-md border border-destructive/50 bg-destructive/10 p-3 text-sm text-destructive">
                Unable to load the request form. Please try again or open it in a new tab.
              </div>
            )}
          </DialogContent>
        </Dialog>
      )}
    </>
  );
}

/**
 * Settings Panel for Request Types Widget
 */
function RequestTypesOptionsGroup() {
  const { updateComponent, selectedComponent, projectKey } = usePortalBuilderStore(
    useShallow((state) => ({
      updateComponent: state.updateComponent,
      selectedComponent: state.selectedComponent,
      projectKey: state.projectKey,
    })),
  );
  const [isGeneratingSubtitle, setIsGeneratingSubtitle] = useState(false);

  const adminUrl = useMemo(() => {
    if (!projectKey) {
      return null;
    }
    const base = getBootstrapBaseUrl();
    if (!base) {
      return null;
    }
    return `${base}/servicedesk/admin/${projectKey}/request-types/`;
  }, [projectKey]);

  if (!selectedComponent) return null;

  const handleChange = (field: string, value: unknown) => {
    updateComponent(selectedComponent.id, field, value, true);
  };

  const handleGenerateSubtitle = async () => {
    const title = selectedComponent.getField("content") || "How can we help you?";
    const existingSubtitle = selectedComponent.getField("description") || "";

    setIsGeneratingSubtitle(true);
    try {
      const generatedSubtitle = await generateRequestTypesSubtitle(title, existingSubtitle || undefined);
      handleChange("description", generatedSubtitle);
    } catch (error) {
      console.error("Failed to generate subtitle:", error);
    } finally {
      setIsGeneratingSubtitle(false);
    }
  };
  const showGroupsValue =
    (selectedComponent.getField("properties.showGroups") ?? "yes") !== "no";
  const variant =
    (selectedComponent.getField("properties.variant") as string | undefined) || "cards";
  const layout = selectedComponent.getField("properties.layout") || "grid";
  const groupLayout =
    (selectedComponent.getField("properties.groupLayout") as string | undefined) ||
    "top-tabs";

  return (
    <div className="space-y-4">
      <div className="space-y-2">
        <Label className="text-sm font-medium">Section Title</Label>
        <Input
          value={selectedComponent.getField("content") || ""}
          onChange={(e) => handleChange("content", e.target.value)}
          placeholder="How can we help you?"
          className="text-sm"
        />
      </div>

      <div className="space-y-2">
        <div className="flex items-center justify-between">
          <Label className="text-sm font-medium">Subtitle (Optional)</Label>
          <Button
            type="button"
            variant="ghost"
            size="sm"
            onClick={handleGenerateSubtitle}
            disabled={isGeneratingSubtitle}
            className="h-6 px-2 text-xs text-muted-foreground hover:text-foreground"
          >
            {isGeneratingSubtitle ? (
              <>
                <Loader2 className="h-3 w-3 mr-1 animate-spin" />
                Generating...
              </>
            ) : (
              <GradientText>
                <CloudGPTIcon size={12} className="mr-1" gradient />
                {selectedComponent.getField("description")?.trim() ? "Enhance" : "Generate"}
              </GradientText>
            )}
          </Button>
        </div>
        <div className={cn(
          "relative rounded-md",
          isGeneratingSubtitle && "ring-2 ring-primary/50 ring-offset-1 animate-pulse"
        )}>
          <Textarea
            value={selectedComponent.getField("description") || ""}
            onChange={(e) => handleChange("description", e.target.value)}
            placeholder="Choose a request type to get started"
            rows={2}
            className="text-sm resize-none"
          />
        </div>
      </div>

      <div className="space-y-2">
        <Label className="text-sm font-medium">Display Variant</Label>
        <Select
          value={variant}
          onValueChange={(value) => handleChange("properties.variant", value)}
        >
          <SelectTrigger className="text-sm">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="cards">Cards (grid or list)</SelectItem>
            <SelectItem value="compact-list">Compact list</SelectItem>
            <SelectItem value="accordion">Accordion</SelectItem>
          </SelectContent>
        </Select>
      </div>

      {variant === "cards" && (
        <>
          <div className="space-y-2">
            <Label className="text-sm font-medium">Layout</Label>
            <Select
              value={layout}
              onValueChange={(value) => handleChange("properties.layout", value)}
            >
              <SelectTrigger className="text-sm">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="grid">Grid</SelectItem>
                <SelectItem value="list">List</SelectItem>
              </SelectContent>
            </Select>
          </div>

          {layout === "grid" && (
            <div className="space-y-2">
              <Label className="text-sm font-medium">Columns</Label>
              <Select
                value={selectedComponent.getField("properties.columns") || "2"}
                onValueChange={(value) => handleChange("properties.columns", value)}
              >
                <SelectTrigger className="text-sm">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="1">1 Column</SelectItem>
                  <SelectItem value="2">2 Columns</SelectItem>
                  <SelectItem value="3">3 Columns</SelectItem>
                  <SelectItem value="4">4 Columns</SelectItem>
                </SelectContent>
              </Select>
            </div>
          )}
        </>
      )}

      <div className="flex items-center space-x-2 pt-2">
        <Checkbox
          id="showGroups"
          checked={showGroupsValue}
          onCheckedChange={(checked) => {
            handleChange("properties.showGroups", checked ? "yes" : "no");
          }}
        />
        <Label
          htmlFor="showGroups"
          className="text-sm font-medium leading-none cursor-pointer"
        >
          Group by Category
        </Label>
      </div>

      {/* Group Layout Selector - Only shown when grouping is enabled */}
      {showGroupsValue && (
        <div className="space-y-2 pl-6 border-l-2 border-muted mt-4">
          <Label className="text-sm font-medium">Group Layout</Label>
          <ToggleGroup
            type="single"
            value={
              (selectedComponent.getField("properties.groupLayout") as string | undefined) ||
              "top-tabs"
            }
            onValueChange={(value) => {
              if (value) handleChange("properties.groupLayout", value);
            }}
            className="flex w-full gap-1"
          >
            <ToggleGroupItem value="top-tabs" className="flex-1 text-xs">
              Top Tabs
            </ToggleGroupItem>
            <ToggleGroupItem value="sectioned" className="flex-1 text-xs">
              Sectioned
            </ToggleGroupItem>
          </ToggleGroup>
        </div>
      )}

      {adminUrl && (
        <div className="space-y-2 border-t pt-4">
          <Label className="text-sm font-medium">Manage in Jira</Label>
          <Button variant="outline" size="sm" asChild>
            <a href={adminUrl} target="_blank" rel="noreferrer">
              Open Request Types
            </a>
          </Button>
          <p className="text-xs text-muted-foreground">
            Use Jira to rename, hide, or reorder request types. Refresh the builder after making changes.
          </p>
        </div>
      )}
    </div>
  );
}

export const RequestTypesDesignProperties = {
  base: null,
  options: <RequestTypesOptionsGroup />,
  grid: null,
  html: null,
  input: null,
  label: null,
  button: null,
  validation: null,
};

export function getReactCodeRequestTypes(component: FormComponentModel): ReactCode {
  const title = component.content || "How can we help you?";

  return {
    code: `<div className="request-types">
  <h2>{${JSON.stringify(title)}}</h2>
  {/* Request types will be loaded from Jira Service Management */}
</div>`,
    dependencies: {},
  };
}
