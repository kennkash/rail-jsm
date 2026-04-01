// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/dto/AnnouncementBannerConfigDTO.java

package com.samsungbuilder.jsm.dto;

import java.util.Objects;

public class AnnouncementBannerConfigDTO {
    private boolean enabled;
    private String title;
    private String message;
    private String icon;
    private String backgroundColor;
    private String borderColor;
    private String textColor;
    private String updatedBy;
    private Long updatedAtEpochMs;

    public AnnouncementBannerConfigDTO() {
    }

    public static AnnouncementBannerConfigDTO defaultDisabled() {
        AnnouncementBannerConfigDTO dto = new AnnouncementBannerConfigDTO();
        dto.setEnabled(false);
        dto.setTitle("");
        dto.setMessage("");
        dto.setIcon("info");
        dto.setBackgroundColor("#EFF6FF");
        dto.setBorderColor("#BFDBFE");
        dto.setTextColor("#1E3A8A");
        return dto;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = safe(title);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = safe(message);
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = safe(icon);
    }

    public String getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(String backgroundColor) {
        this.backgroundColor = safe(backgroundColor);
    }

    public String getBorderColor() {
        return borderColor;
    }

    public void setBorderColor(String borderColor) {
        this.borderColor = safe(borderColor);
    }

    public String getTextColor() {
        return textColor;
    }

    public void setTextColor(String textColor) {
        this.textColor = safe(textColor);
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = safe(updatedBy);
    }

    public Long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(Long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof AnnouncementBannerConfigDTO)) return false;
        AnnouncementBannerConfigDTO that = (AnnouncementBannerConfigDTO) o;
        return enabled == that.enabled
            && Objects.equals(title, that.title)
            && Objects.equals(message, that.message)
            && Objects.equals(icon, that.icon)
            && Objects.equals(backgroundColor, that.backgroundColor)
            && Objects.equals(borderColor, that.borderColor)
            && Objects.equals(textColor, that.textColor)
            && Objects.equals(updatedBy, that.updatedBy)
            && Objects.equals(updatedAtEpochMs, that.updatedAtEpochMs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            enabled, title, message, icon,
            backgroundColor, borderColor, textColor,
            updatedBy, updatedAtEpochMs
        );
    }
}
