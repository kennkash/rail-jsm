// /rail-at-sas/frontend/lib/api/announcement-banner-client.ts
const API_BASE = "/rest/rail/1.0";

export type AnnouncementBannerConfig = {
  enabled: boolean;
  title: string;
  message: string;
  icon: string;
  backgroundColor: string;
  borderColor: string;
  textColor: string;
  updatedBy?: string;
  updatedAtEpochMs?: number;
};

export async function fetchAnnouncementBanner(): Promise<AnnouncementBannerConfig> {
  const response = await fetch(`${API_BASE}/announcement-banner`, {
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch announcement banner: ${response.status}`);
  }

  return response.json();
}

export async function fetchAnnouncementBannerAdmin(): Promise<AnnouncementBannerConfig> {
  const response = await fetch(`${API_BASE}/announcement-banner/admin`, {
    credentials: "same-origin",
  });

  if (!response.ok) {
    throw new Error(`Failed to fetch admin announcement banner config: ${response.status}`);
  }

  return response.json();
}

export async function saveAnnouncementBanner(
  payload: AnnouncementBannerConfig,
): Promise<AnnouncementBannerConfig> {
  const response = await fetch(`${API_BASE}/announcement-banner/admin`, {
    method: "PUT",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    throw new Error(`Failed to save announcement banner: ${response.status}`);
  }

  return response.json();
}
