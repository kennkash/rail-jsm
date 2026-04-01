"use client";

import {
  Info,
  TriangleAlert,
  BadgeAlert,
  Megaphone,
  CircleAlert,
  CheckCircle2,
  type LucideIcon,
} from "lucide-react";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { cn } from "@/lib/utils";

export type AnnouncementBannerConfig = {
  enabled: boolean;
  title: string;
  message: string;
  icon: string;
  backgroundColor: string;
  borderColor: string;
  textColor: string;
};

const ICON_MAP: Record<string, LucideIcon> = {
  info: Info,
  "triangle-alert": TriangleAlert,
  "badge-alert": BadgeAlert,
  megaphone: Megaphone,
  "circle-alert": CircleAlert,
  "check-circle": CheckCircle2,
};

type RailAnnouncementBannerProps = {
  config: AnnouncementBannerConfig | null;
  className?: string;
};

export function RailAnnouncementBanner({
  config,
  className,
}: RailAnnouncementBannerProps) {
  if (!config?.enabled) return null;

  const Icon = ICON_MAP[config.icon] ?? Info;

  return (
    <div className={cn("border-b bg-background", className)}>
      <div className="px-6 py-3">
        <div className="max-w-5xl mx-auto">
          <Alert
            className="rounded-lg border"
            style={{
              backgroundColor: config.backgroundColor,
              borderColor: config.borderColor,
              color: config.textColor,
            }}
          >
            <Icon className="h-4 w-4 mt-0.5" />
            <div>
              {config.title ? <AlertTitle>{config.title}</AlertTitle> : null}
              {config.message ? (
                <AlertDescription className="whitespace-pre-wrap">
                  {config.message}
                </AlertDescription>
              ) : null}
            </div>
          </Alert>
        </div>
      </div>
    </div>
  );
}
