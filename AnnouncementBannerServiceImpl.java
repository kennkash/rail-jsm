// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/service/AnnouncementBannerServiceImpl.java

package com.samsungbuilder.jsm.service;

import com.atlassian.sal.api.pluginsettings.PluginSettings;
import com.atlassian.sal.api.pluginsettings.PluginSettingsFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;

@Named
public class AnnouncementBannerServiceImpl implements AnnouncementBannerService {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementBannerServiceImpl.class);

    private static final String STORAGE_KEY = "com.samsungbuilder.jsm.rail-portal.announcementBanner";
    private static final int MAX_TITLE_LEN = 120;
    private static final int MAX_MESSAGE_LEN = 1000;

    private final PluginSettingsFactory pluginSettingsFactory;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Inject
    public AnnouncementBannerServiceImpl(PluginSettingsFactory pluginSettingsFactory) {
        this.pluginSettingsFactory = pluginSettingsFactory;
    }

    @Override
    public AnnouncementBannerConfigDTO getConfig() {
        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            Object raw = settings.get(STORAGE_KEY);
            if (!(raw instanceof String) || ((String) raw).trim().isEmpty()) {
                return AnnouncementBannerConfigDTO.defaultDisabled();
            }

            AnnouncementBannerConfigDTO dto =
                objectMapper.readValue((String) raw, AnnouncementBannerConfigDTO.class);

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
            settings.put(STORAGE_KEY, objectMapper.writeValueAsString(normalized));
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
}
