package com.samsungbuilder.jsm.service;

import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class AnnouncementBannerServiceImpl implements AnnouncementBannerService {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementBannerServiceImpl.class);

    private static final String KEY_PREFIX = "com.samsungbuilder.jsm.rail-portal.announcementBanner.";
    private static final String KEY_ENABLED = KEY_PREFIX + "enabled";
    private static final String KEY_TITLE = KEY_PREFIX + "title";
    private static final String KEY_MESSAGE = KEY_PREFIX + "message";
    private static final String KEY_ICON = KEY_PREFIX + "icon";
    private static final String KEY_BACKGROUND_COLOR = KEY_PREFIX + "backgroundColor";
    private static final String KEY_BORDER_COLOR = KEY_PREFIX + "borderColor";
    private static final String KEY_TEXT_COLOR = KEY_PREFIX + "textColor";
    private static final String KEY_UPDATED_BY = KEY_PREFIX + "updatedBy";
    private static final String KEY_UPDATED_AT = KEY_PREFIX + "updatedAtEpochMs";

    private static final int MAX_TITLE_LEN = 120;
    private static final int MAX_MESSAGE_LEN = 1000;

    private final PluginSettingsFactory pluginSettingsFactory;

    @Inject
    public AnnouncementBannerServiceImpl(@ComponentImport PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public AnnouncementBannerConfigDTO getConfig() {
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();

            AnnouncementBannerConfigDTO dto = AnnouncementBannerConfigDTO.defaultDisabled();
            dto.setEnabled(parseBoolean(settings.get(KEY_ENABLED), false));
            dto.setTitle(readString(settings.get(KEY_TITLE), ""));
            dto.setMessage(readString(settings.get(KEY_MESSAGE), ""));
            dto.setIcon(validateIcon(readString(settings.get(KEY_ICON), "info")));
            dto.setBackgroundColor(validateHex(readString(settings.get(KEY_BACKGROUND_COLOR), "#EFF6FF"), "#EFF6FF"));
            dto.setBorderColor(validateHex(readString(settings.get(KEY_BORDER_COLOR), "#BFDBFE"), "#BFDBFE"));
            dto.setTextColor(validateHex(readString(settings.get(KEY_TEXT_COLOR), "#1E3A8A"), "#1E3A8A"));
            dto.setUpdatedBy(readString(settings.get(KEY_UPDATED_BY), ""));
            dto.setUpdatedAtEpochMs(parseLong(settings.get(KEY_UPDATED_AT)));

            return normalize(dto);
        } catch (Exception e) {
            log.error("Failed to load announcement banner config", e);
            return AnnouncementBannerConfigDTO.defaultDisabled();
        }
    }

    @Override
    public AnnouncementBannerConfigDTO saveConfig(AnnouncementBannerConfigDTO config, String updatedBy) {
        AnnouncementBannerConfigDTO normalized = normalize(config);
        normalized.setUpdatedBy(updatedBy);
        normalized.setUpdatedAtEpochMs(System.currentTimeMillis());

        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            settings.put(KEY_ENABLED, String.valueOf(normalized.isEnabled()));
            settings.put(KEY_TITLE, normalized.getTitle());
            settings.put(KEY_MESSAGE, normalized.getMessage());
            settings.put(KEY_ICON, normalized.getIcon());
            settings.put(KEY_BACKGROUND_COLOR, normalized.getBackgroundColor());
            settings.put(KEY_BORDER_COLOR, normalized.getBorderColor());
            settings.put(KEY_TEXT_COLOR, normalized.getTextColor());
            settings.put(KEY_UPDATED_BY, normalized.getUpdatedBy());
            settings.put(KEY_UPDATED_AT, String.valueOf(normalized.getUpdatedAtEpochMs()));

            log.info(
                "Saved RAIL announcement banner config. enabled={}, icon={}, updatedBy={}",
                normalized.isEnabled(),
                normalized.getIcon(),
                updatedBy
            );

            return normalized;
        } catch (Exception e) {
            log.error("Failed to save announcement banner config", e);
            throw new IllegalStateException("Unable to save announcement banner configuration", e);
        }
    }

    private AnnouncementBannerConfigDTO normalize(AnnouncementBannerConfigDTO input) {
        AnnouncementBannerConfigDTO dto = input == null
            ? AnnouncementBannerConfigDTO.defaultDisabled()
            : input;

        AnnouncementBannerConfigDTO normalized = new AnnouncementBannerConfigDTO();
        normalized.setEnabled(dto.isEnabled());
        normalized.setTitle(limit(dto.getTitle(), MAX_TITLE_LEN));
        normalized.setMessage(limit(dto.getMessage(), MAX_MESSAGE_LEN));
        normalized.setIcon(validateIcon(dto.getIcon()));
        normalized.setBackgroundColor(validateHex(dto.getBackgroundColor(), "#EFF6FF"));
        normalized.setBorderColor(validateHex(dto.getBorderColor(), "#BFDBFE"));
        normalized.setTextColor(validateHex(dto.getTextColor(), "#1E3A8A"));
        normalized.setUpdatedBy(dto.getUpdatedBy());
        normalized.setUpdatedAtEpochMs(dto.getUpdatedAtEpochMs());
        return normalized;
    }

    private String limit(String value, int max) {
        String safe = value == null ? "" : value.trim();
        return safe.length() > max ? safe.substring(0, max) : safe;
    }

    private String validateIcon(String value) {
        String safe = value == null ? "" : value.trim().toLowerCase();
        switch (safe) {
            case "info":
            case "triangle-alert":
            case "badge-alert":
            case "megaphone":
            case "circle-alert":
            case "check-circle":
                return safe;
            default:
                return "info";
        }
    }

    private String validateHex(String value, String fallback) {
        String safe = value == null ? "" : value.trim();
        return safe.matches("^#([A-Fa-f0-9]{6})$") ? safe : fallback;
    }

    private String readString(Object raw, String fallback) {
        return raw == null ? fallback : String.valueOf(raw).trim();
    }

    private boolean parseBoolean(Object raw, boolean fallback) {
        if (raw == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(raw));
    }

    private Long parseLong(Object raw) {
        if (raw == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
