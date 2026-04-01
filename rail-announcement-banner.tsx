"use client";

import DOMPurify from "isomorphic-dompurify";
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
  compact?: boolean;
};

export function RailAnnouncementBanner({
  config,
  className,
  compact = false,
}: RailAnnouncementBannerProps) {
  if (!config?.enabled) return null;

  const Icon = ICON_MAP[config.icon] ?? Info;

  const sanitizedMessage = config.message
    ? DOMPurify.sanitize(config.message, {
        ALLOWED_TAGS: [
          "a",
          "b",
          "strong",
          "i",
          "em",
          "u",
          "br",
          "p",
          "ul",
          "ol",
          "li",
          "span",
        ],
        ALLOWED_ATTR: ["href", "target", "rel"],
      })
    : "";

  const alertContent = (
    <Alert
      className={cn("rounded-lg border", compact && "shadow-none", className)}
      style={{
        backgroundColor: config.backgroundColor,
        borderColor: config.borderColor,
        color: config.textColor,
      }}
    >
      <Icon className="h-4 w-4 mt-0.5" />
      <div>
        {config.title ? <AlertTitle>{config.title}</AlertTitle> : null}
        {sanitizedMessage ? (
          <AlertDescription
            className="whitespace-pre-wrap [&_a]:font-medium [&_a]:underline"
            dangerouslySetInnerHTML={{ __html: sanitizedMessage }}
          />
        ) : null}
      </div>
    </Alert>
  );

  if (compact) {
    return alertContent;
  }

  return (
    <div className="border-b bg-background">
      <div className="px-6 py-3">
        <div className="mx-auto max-w-5xl">{alertContent}</div>
      </div>
    </div>
  );
}
