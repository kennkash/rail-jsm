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
  if (typeof window === "undefined") return "";
  const bootstrap = (window as BootstrapWindow).RAIL_PORTAL_BOOTSTRAP;
  return (bootstrap?.baseUrl ?? window.location.origin).replace(/\/$/, "");
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
  header#header, .cv-bg-image, #footer { display: none !important; }
  #content-wrapper { margin: 0px !important; width: 100% !important; padding: 20px 75px 0px !important; }
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

  // Icon Fetching Logic
  useEffect(() => {
    if (!resolvedServiceDeskId) { setJsmIconUrls(null); return; }
    let cancelled = false;
    const fetchIcons = async () => {
      try {
        const base = getBootstrapBaseUrl();
        const response = await fetch(`${base}/rest/servicedeskapi/servicedesk/${resolvedServiceDeskId}/requesttype?limit=100`, { credentials: 'same-origin' });
        if (!response.ok) return;
        const payload = await response.json();
        const mapping: Record<string, string> = {};
        (payload?.values || []).forEach((v: any) => {
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

  // FIX: Mapping and Sorting
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

    const sectionsMap: Record<string, RequestTypeOption[]> = {};
    requestTypes.forEach((type) => {
      const cats = type.groups?.length ? type.groups : [type.group || "Other"];
      cats.forEach(c => {
        if (!sectionsMap[c]) sectionsMap[c] = [];
        sectionsMap[c].push(type);
      });
    });

    const apiGroups = data?.groups || [];
    const groupNameToId = new Map(apiGroups.map(g => [g.name, g.id]));
    const sortedNames = apiGroups.length > 0 ? apiGroups.map(g => g.name) : Object.keys(sectionsMap).sort();

    return sortedNames
      .filter(name => sectionsMap[name])
      .map(name => {
        const groupId = groupNameToId.get(name);
        const sortedItems = [...sectionsMap[name]].sort((a, b) => {
          const orderA = groupId ? (a.groupOrderMap?.[groupId] ?? 999) : (a.displayOrder ?? 999);
          const orderB = groupId ? (b.groupOrderMap?.[groupId] ?? 999) : (b.displayOrder ?? 999);
          return orderA !== orderB ? orderA - orderB : a.name.localeCompare(b.name);
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

  // Dialog & Interaction Handlers
  const handleRequestTypeClick = (type: RequestTypeOption) => {
    const base = getBootstrapBaseUrl();
    const portalId = resolvedServiceDeskId || resolvedApiProjectKey?.toLowerCase() + "-portal";
    const url = `${base}/servicedesk/customer/portal/${portalId}/create/${type.id}`;
    setIframeUrl(url);
    setIframeStatus("loading");
    setIsDialogOpen(true);
  };

  const renderTypeAvatar = (type: RequestTypeOption, sizeClass = "h-8 w-8") => (
    <Avatar className={cn(sizeClass, "!rounded-none")}>
      {type.iconUrl && <AvatarImage src={type.iconUrl} />}
      <AvatarFallback className="text-xs !rounded-none">{type.name.charAt(0)}</AvatarFallback>
    </Avatar>
  );

  const renderRequestTypes = (types: RequestTypeOption[]) => {
    const columnCount = Math.min(Math.max(Number(columns) || 2, 1), 4);
    return (
      <div className={cn(layout === "grid" ? "grid gap-4" : "flex flex-col gap-4")}
           style={layout === "grid" ? { gridTemplateColumns: `repeat(${columnCount}, minmax(260px, 1fr))` } : undefined}>
        {types.map((type) => (
          <Card key={type.id} className="cursor-pointer hover:shadow-md transition-all" onClick={() => handleRequestTypeClick(type)}>
            <CardHeader className="space-y-2">
              <div className="flex items-center gap-4">
                {renderTypeAvatar(type, "h-7 w-7")}
                <CardTitle className="text-lg font-semibold">{type.name}</CardTitle>
              </div>
              {type.description && <CardDescription className="text-sm line-clamp-2">{type.description}</CardDescription>}
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

      {isLoading ? <Skeleton className="h-40 w-full" /> : (
        showGroups && groupedSections.length > 1 ? (
          groupLayout === "top-tabs" ? (
            <Tabs value={activeGroup} onValueChange={setActiveGroup} className="w-full">
              <TabsList className="flex flex-wrap h-auto bg-transparent gap-2">
                {groupedSections.map(s => (
                  <TabsTrigger key={s.name} value={s.name} className="data-[state=active]:bg-muted border">
                    {s.name} <Badge variant="secondary" className="ml-2">{s.types.length}</Badge>
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
        )
      )}

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent className="sm:max-w-[800px] h-[90vh]">
          {iframeUrl && (
            <div className="relative h-full w-full">
              {iframeStatus === "loading" && (
                <div className="absolute inset-0 flex items-center justify-center bg-background/80">
                  <Loader2 className="animate-spin h-8 w-8" />
                </div>
              )}
              <iframe
                src={iframeUrl}
                className="w-full h-full border-0"
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
