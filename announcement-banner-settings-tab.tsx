<div className="space-y-2">
  <Label>Preview</Label>
  <div className="rounded-md border bg-muted/30 p-3">
    {form.enabled ? (
      <RailAnnouncementBanner config={form} compact />
    ) : (
      <p className="text-sm text-muted-foreground">
        Preview will appear here when the banner is enabled.
      </p>
    )}
  </div>
</div>


// /rail-at-sas/frontend/components/admin/announcement-banner-settings-tab.tsx

"use client";

import { useEffect, useState } from "react";
import { Save } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import {
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from "@/components/ui/select";
import {
    fetchAnnouncementBannerAdmin,
    saveAnnouncementBanner,
    type AnnouncementBannerConfig,
} from "@/lib/api/announcement-banner-client";
import { RailAnnouncementBanner } from "@/components/landing/rail-announcement-banner";
import { toast } from "sonner";

const DEFAULTS: AnnouncementBannerConfig = {
    enabled: false,
    title: "",
    message: "",
    icon: "info",
    backgroundColor: "#EFF6FF",
    borderColor: "#BFDBFE",
    textColor: "#1E3A8A",
};

export function AnnouncementBannerSettingsTab() {
    const [form, setForm] = useState<AnnouncementBannerConfig>(DEFAULTS);
    const [isSaving, setIsSaving] = useState(false);

    useEffect(() => {
        const load = async () => {
            try {
                const data = await fetchAnnouncementBannerAdmin();
                setForm(data);
            } catch (error) {
                console.error(error);
                toast.error("Failed to load announcement banner settings");
            }
        };

        void load();
    }, []);

    const update = <K extends keyof AnnouncementBannerConfig>(key: K, value: AnnouncementBannerConfig[K]) => {
        setForm((prev) => ({ ...prev, [key]: value }));
    };

    const onSave = async () => {
        setIsSaving(true);
        try {
            const saved = await saveAnnouncementBanner(form);
            setForm(saved);
            toast.success("Announcement banner updated");
        } catch (error) {
            console.error(error);
            toast.error("Failed to save announcement banner");
        } finally {
            setIsSaving(false);
        }
    };

    return (
        <Card>
            <CardHeader>
                <CardTitle>Homepage Announcement Banner</CardTitle>
                <CardDescription>
                    Configure the banner shown on the RAIL homepage for all logged-in users.
                </CardDescription>
            </CardHeader>
            <CardContent className="space-y-6">
                <div className="flex items-center justify-between rounded-md border p-4">
                    <div className="space-y-1">
                        <Label htmlFor="announcement-enabled">Enable banner</Label>
                        <p className="text-sm text-muted-foreground">
                            Toggle the global homepage announcement on or off.
                        </p>
                    </div>
                    <Switch
                        id="announcement-enabled"
                        checked={form.enabled}
                        onCheckedChange={(checked) => update("enabled", checked)}
                    />
                </div>

                <div className="grid gap-4 md:grid-cols-2">
                    <div className="space-y-2">
                        <Label>Title</Label>
                        <Input
                            value={form.title}
                            onChange={(e) => update("title", e.target.value)}
                            placeholder="Scheduled maintenance"
                            maxLength={120}
                        />
                    </div>

                    <div className="space-y-2">
                        <Label>Icon</Label>
                        <Select value={form.icon} onValueChange={(value) => update("icon", value)}>
                            <SelectTrigger>
                                <SelectValue placeholder="Select an icon" />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value="info">Info</SelectItem>
                                <SelectItem value="triangle-alert">Triangle Alert</SelectItem>
                                <SelectItem value="badge-alert">Badge Alert</SelectItem>
                                <SelectItem value="megaphone">Megaphone</SelectItem>
                                <SelectItem value="circle-alert">Circle Alert</SelectItem>
                                <SelectItem value="check-circle">Check Circle</SelectItem>
                            </SelectContent>
                        </Select>
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>Message</Label>
                    <Textarea
                        value={form.message}
                        onChange={(e) => update("message", e.target.value)}
                        placeholder="RAIL will be unavailable Sunday from 8:00 AM to 10:00 AM Central Time."
                        rows={4}
                        maxLength={1000}
                    />
                </div>

                <div className="grid gap-4 md:grid-cols-3">
                    <div className="space-y-2">
                        <Label>Background color</Label>
                        <Input
                            value={form.backgroundColor}
                            onChange={(e) => update("backgroundColor", e.target.value)}
                            placeholder="#EFF6FF"
                        />
                    </div>

                    <div className="space-y-2">
                        <Label>Border color</Label>
                        <Input
                            value={form.borderColor}
                            onChange={(e) => update("borderColor", e.target.value)}
                            placeholder="#BFDBFE"
                        />
                    </div>

                    <div className="space-y-2">
                        <Label>Text color</Label>
                        <Input
                            value={form.textColor}
                            onChange={(e) => update("textColor", e.target.value)}
                            placeholder="#1E3A8A"
                        />
                    </div>
                </div>

                <div className="space-y-2">
                    <Label>Preview</Label>
                    <RailAnnouncementBanner config={form} className="border-0 bg-transparent px-0 py-0" />
                </div>

                <div className="flex justify-end">
                    <Button onClick={onSave} disabled={isSaving} className="cursor-pointer">
                        <Save className="mr-2 h-4 w-4" />
                        {isSaving ? "Saving..." : "Save announcement"}
                    </Button>
                </div>
            </CardContent>
        </Card>
    );
}
