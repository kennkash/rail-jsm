'use client'

import { FormComponentModel } from "@/models/FormComponent";
import { UseFormReturn, ControllerRenderProps, FieldValues } from "react-hook-form";
import { Card, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Skeleton } from "@/components/ui/skeleton";
import { cn } from "@/lib/utils";
import { usePortalBuilderStore } from "@/stores/portal-builder-store";
import { useRequestTypes } from "@/hooks/use-request-types";
import { useEffect, useMemo, useState } from "react";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

type RequestTypeOption = {
  id: string;
  name: string;
  description?: string;
  groups?: string[];
  displayOrder?: number;
  groupOrderMap?: Record<string, number>;
};

export function PortalRequestTypes(component: FormComponentModel) {
  const portalProjectKey = usePortalBuilderStore((state) => state.projectKey);
  const { data, isLoading } = useRequestTypes(portalProjectKey) as { data?: any; isLoading: boolean };
  const [activeGroup, setActiveGroup] = useState<string | undefined>(undefined);

  const requestTypes = useMemo(() => {
    return (data?.requestTypes || []).map((type: any) => ({
      ...type,
      groupOrderMap: type.groupOrderMap || {},
    }));
  }, [data]);

  const groupedSections = useMemo(() => {
    if (!requestTypes.length) return [];
    
    const sections: Record<string, RequestTypeOption[]> = {};
    requestTypes.forEach((type: RequestTypeOption) => {
      const cats = type.groups?.length ? type.groups : ["Other"];
      cats.forEach(c => {
        if (!sections[c]) sections[c] = [];
        sections[c].push(type);
      });
    });

    const apiGroups = data?.groups || [];
    const groupNameToId = new Map(apiGroups.map((g: any) => [g.name, g.id]));
    const sortedNames = apiGroups.length > 0 ? apiGroups.map((g: any) => g.name) : Object.keys(sections).sort();

    return sortedNames.map(name => {
      const groupId = groupNameToId.get(name);
      const sortedItems = [...(sections[name] || [])].sort((a, b) => {
        const orderA = groupId ? (a.groupOrderMap?.[groupId] ?? 999) : 999;
        const orderB = groupId ? (b.groupOrderMap?.[groupId] ?? 999) : 999;
        return orderA - orderB || a.name.localeCompare(b.name);
      });
      return { name, types: sortedItems };
    }).filter(s => s.types.length > 0);
  }, [data?.groups, requestTypes]);

  useEffect(() => {
    if (groupedSections.length > 0 && !activeGroup) setActiveGroup(groupedSections[0].name);
  }, [groupedSections, activeGroup]);

  if (isLoading) return <Skeleton className="h-40 w-full" />;

  return (
    <div className="w-full space-y-6">
      <Tabs value={activeGroup} onValueChange={setActiveGroup}>
        <TabsList className="flex flex-wrap h-auto bg-transparent gap-2">
          {groupedSections.map(s => (
            <TabsTrigger key={s.name} value={s.name} className="border">
              {s.name} <Badge variant="secondary" className="ml-2">{s.types.length}</Badge>
            </TabsTrigger>
          ))}
        </TabsList>
        {groupedSections.map(s => (
          <TabsContent key={s.name} value={s.name} className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
            {s.types.map(type => (
              <Card key={type.id} className="p-4 flex gap-4 items-center">
                <Avatar className="h-10 w-10 !rounded-none"><AvatarFallback>{type.name[0]}</AvatarFallback></Avatar>
                <div>
                  <CardTitle className="text-md">{type.name}</CardTitle>
                  <CardDescription>{type.description}</CardDescription>
                </div>
              </Card>
            ))}
          </TabsContent>
        ))}
      </Tabs>
    </div>
  );
}
