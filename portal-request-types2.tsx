// rail-at-sas/frontend/components/portal-builder/form-components/portal-request-types.tsx
'use client'

import { FormComponentModel } from "@/models/FormComponent";
import { UseFormReturn, ControllerRenderProps, FieldValues } from "react-hook-form";
import { ReactCode, Viewports } from "@/types/portal-builder.types";
import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Separator } from "@/components/ui/separator";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { usePortalBuilderStore } from "@/stores/portal-builder-store";
import { useRequestTypes } from "@/hooks/use-request-types";
import { useEffect, useMemo, useRef, useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Loader2 } from "lucide-react";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import { buildRequestTypeUrl } from "@/types/router";

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
  header#header { display: none !important; }
  .cv-bg-image { display: none !important; }
  #content-wrapper { margin: 0px !important; width: 100% !important; padding: 20px 75px 0px !important; }
  #footer { display: none !important; }
`;

export function PortalRequestTypes(
  component: FormComponentModel,
  form: UseFormReturn<FieldValues, undefined>,
  field: ControllerRenderProps,
  viewport?: Viewports,
  routingOptions?: RequestTypeRoutingOptions,
) {
  const title = component.getField("content", viewport) || "How can we help you?";
  const subtitle = component.getField("description", viewport) || "";
  const variant = (component.getField("properties.variant", viewport) as string | undefined) || "cards";
  const layout = component.getField("properties.layout", viewport) || "grid";
  const columns = component.getField("properties.columns", viewport) || "2";
  const showGroups = (component.getField("properties.showGroups", viewport) ?? "yes") !== "no";
  const groupLayout = (component.getField("properties.groupLayout", viewport) as string | undefined) || "top-tabs";
  
  const portalProjectKey = usePortalBuilderStore((state) => state.projectKey);
  const portalIdFromStore = usePortalBuilderStore((state) => state.portalId);
  const serviceDeskIdFromStore = usePortalBuilderStore((state) => state.serviceDeskId);
  const isLiveFromStore = usePortalBuilderStore((state) => state.isLive);
  const mode = usePortalBuilderStore((state) => state.mode);
  const isCustomerPortal = mode === "preview";
  
  const configuredProjectKey = component.getField("properties.projectKey", viewport) as string | undefined;
  const resolvedApiProjectKey = portalProjectKey || configuredProjectKey || null;
  const resolvedServiceDeskId = serviceDeskIdFromStore || null;
  
  const { data, isLoading } = useRequestTypes(resolvedApiProjectKey) as {
    data?: RequestTypesApiResponse;
    isLoading: boolean;
  };
  
  const [jsmIconUrls, setJsmIconUrls] = useState<Record<string, string> | null>(null);

  // Icon fetching logic
  useEffect(() => {
    if (!resolvedServiceDeskId) { setJsmIconUrls(null); return; }
    let cancelled = false;
    const fetchIcons = async () => {
      try {
        const base = getBootstrapBaseUrl();
        if (!base) return;
        const response = await fetch(
          `${base}/rest/servicedeskapi/servicedesk/${resolvedServiceDeskId}/requesttype?limit=100`,
          { credentials: 'same-origin' }
        );
        if (!response.ok) return;
        const payload = await response.json();
        const mapping: Record<string, string> = {};
        (payload?.values || []).forEach((v: any) => {
          if (!v || !v.id) return;
          const url = v.icon?._links?.iconUrls?.["32x32"] || v.icon?._links?.iconUrls?.["24x24"];
          if (url) mapping[v.id] = url;
        });
        if (!cancelled) setJsmIconUrls(mapping);
      } catch (e) {}
    };
    fetchIcons();
    return () => { cancelled = true; };
  }, [resolvedServiceDeskId]);

  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [iframeUrl, setIframeUrl] = useState<string | null>(null);
  const [iframeStatus, setIframeStatus] = useState<"idle" | "loading" | "ready" | "error">("idle");
  const iframeRef = useRef<HTMLIFrameElement | null>(null);
  const routeProjectKey = routingOptions?.routeProjectKey || resolvedApiProjectKey || portalProjectKey || undefined;
  const onRouteChange = routingOptions?.onRouteChange;

  // Process and sort request types
  const apiRequestTypes = useMemo<RequestTypeOption[] | null>(() => {
    if (!data?.requestTypes?.length) return null;
    return data.requestTypes.map((type) => ({
      ...type,
      iconUrl: jsmIconUrls?.[type.id] || type.iconUrl,
      displayOrder: type.displayOrder ?? 999,
      groupOrderMap: type.groupOrderMap || {},
    }));
  }, [data, jsmIconUrls]);

  const requestTypes = apiRequestTypes || [];

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

    // 3. Sort Group Tabs
    const sortedGroupNames = apiGroups.length > 0 
      ? apiGroups.map(g => g.name) 
      : Object.keys(sections).sort();

    // 4. Sort Items Within Each Group
    return sortedGroupNames
      .filter(name => sections[name])
      .map(name => {
        const groupId = groupNameToId.get(name);
        const sortedItems = [...sections[name]].sort((a, b) => {
          // Look up specific order for this group ID
          const orderA = groupId ? (a.groupOrderMap?.[groupId] ?? 999) : 999;
          const orderB = groupId ? (b.groupOrderMap?.[groupId] ?? 999) : 999;
          
          if (orderA !== orderB) return orderA - orderB;
          return a.name.localeCompare(b.name);
        });
        return { name, types: sortedItems };
      });
  }, [data?.groups, requestTypes]);

  const [activeGroup, setActiveGroup] = useState<string | undefined>(undefined);

  useEffect(() => {
    if (isCustomerPortal && groupedSections.length > 0 && !activeGroup) {
      setActiveGroup(groupedSections[0].name);
    }
  }, [isCustomerPortal, groupedSections, activeGroup]);

  // Handlers for click/dialog
  const handleRequestTypeClick = (type: RequestTypeOption) => {
    const base = getBootstrapBaseUrl();
    if (!base) return;
    const portalId = resolvedServiceDeskId || portalIdFromStore || "default";
    const url = `${base}/servicedesk/customer/portal/${portalId}/create/${type.id}`;
    
    setIframeUrl(url);
    setIframeStatus("loading");
    setIsDialogOpen(true);
    
    if (onRouteChange && routeProjectKey) {
      const effectiveIsLive = isCustomerPortal || isLiveFromStore;
      const targetPath = buildRequestTypeUrl(effectiveIsLive, routeProjectKey, type.id, resolvedServiceDeskId ?? undefined);
      onRouteChange(targetPath, false);
    }
  };

  const renderTypeAvatar = (type: RequestTypeOption, sizeClass = "h-8 w-8") => (
    <Avatar className={cn(sizeClass, "!rounded-none")}>
      {type.iconUrl && <AvatarImage src={type.iconUrl} />}
      <AvatarFallback className="text-xs font-medium !rounded-none">{type.name.charAt(0)}</AvatarFallback>
    </Avatar>
  );

  const renderRequestTypes = (types: RequestTypeOption[]) => {
    if (variant === "compact-list") {
      return (
        <div className="divide-y rounded-md border bg-background">
          {types.map((type) => (
            <button key={type.id} className="flex w-full items-start gap-3 px-3 py-3 text-left text-sm hover:bg-muted" onClick={() => handleRequestTypeClick(type)}>
              <div className="mt-0.5">{renderTypeAvatar(type)}</div>
              <div className="flex-1">
                <div className="font-medium">{type.name}</div>
                {type.description && <p className="text-xs text-muted-foreground line-clamp-2">{type.description}</p>}
              </div>
            </button>
          ))}
        </div>
      );
    }

    if (variant === "accordion") {
      return (
        <Accordion type="single" collapsible className="w-full rounded-md border bg-background">
          {types.map((type) => (
            <AccordionItem key={type.id} value={type.id}>
              <AccordionTrigger className="px-3 py-2">
                <div className="flex items-start gap-3">
                  <div className="mt-0.5">{renderTypeAvatar(type)}</div>
                  <div className="text-left"><div className="font-medium">{type.name}</div></div>
                </div>
              </AccordionTrigger>
              <AccordionContent className="px-3 pb-3">
                <Button size="sm" onClick={() => handleRequestTypeClick(type)}>Open request</Button>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      );
    }

    const columnCount = Math.min(Math.max(Number(columns) || 2, 1), 4);
    return (
      <div className={cn(layout === "grid" ? "grid gap-4" : "flex flex-col gap-4")}
           style={layout === "grid" ? { gridTemplateColumns: `repeat(${columnCount}, minmax(260px, 1fr))` } : undefined}>
        {types.map((type) => (
          <Card key={type.id} className="cursor-pointer hover:shadow-md transition-all" onClick={() => handleRequestTypeClick(type)}>
            <CardHeader className="space-y-2">
              <div className="flex items-center gap-4">
                {renderTypeAvatar(type, "h-7 w-7")}
                <CardTitle className="text-lg font-semibold text-left">{type.name}</CardTitle>
              </div>
              {type.description && <CardDescription className="text-sm text-left line-clamp-2">{type.description}</CardDescription>}
            </CardHeader>
          </Card>
        ))}
      </div>
    );
  };

  return (
    <div className="w-full space-y-6">
      {(title || subtitle) && (
        <div className="space-y-2">
          {title && <h2 className="text-2xl font-bold">{title}</h2>}
          {subtitle && <p className="text-muted-foreground">{subtitle}</p>}
        </div>
      )}

      {isLoading ? (
        <Skeleton className="h-40 w-full" />
      ) : showGroups && groupedSections.length > 1 ? (
        groupLayout === "top-tabs" ? (
          <Tabs value={activeGroup} onValueChange={setActiveGroup} className="w-full">
            <TabsList className="flex flex-wrap h-auto bg-transparent gap-2 justify-start">
              {groupedSections.map(s => (
                <TabsTrigger key={s.name} value={s.name} className="px-3 py-1.5 border data-[state=active]:bg-muted">
                  {s.name} <Badge variant="secondary" className="ml-2 rounded-full">{s.types.length}</Badge>
                </TabsTrigger>
              ))}
            </TabsList>
            {groupedSections.map(s => (
              <TabsContent key={s.name} value={s.name} className="mt-6">
                {renderRequestTypes(s.types)}
              </TabsContent>
            ))}
          </Tabs>
        ) : (
          <div className="space-y-8">
            {groupedSections.map(s => (
              <div key={s.name} className="space-y-4">
                <div className="flex items-center justify-between">
                  <h3 className="text-lg font-semibold">{s.name}</h3>
                  <Badge variant="outline">{s.types.length} types</Badge>
                </div>
                <Separator />
                {renderRequestTypes(s.types)}
              </div>
            ))}
          </div>
        )
      ) : (
        renderRequestTypes(requestTypes)
      )}

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="sm:max-w-[800px] h-[90vh]">
          {iframeUrl && (
            <div className="relative h-full w-full">
               {iframeStatus === "loading" && (
                <div className="absolute inset-0 flex items-center justify-center bg-background/80 z-10">
                  <Loader2 className="animate-spin h-8 w-8 text-primary" />
                </div>
              )}
              <iframe
                ref={iframeRef}
                src={iframeUrl}
                className="w-full h-full border-0 bg-white"
                onLoad={() => setIframeStatus("ready")}
                onError={() => setIframeStatus("error")}
              />
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
