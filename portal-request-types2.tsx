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
    const groupNameToId = new Map(apiGroups.map(g => [g.name,
