package com.samsungbuilder.jsm.service;

import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;

public interface AnnouncementBannerService {
    AnnouncementBannerConfigDTO getConfig();
    AnnouncementBannerConfigDTO saveConfig(AnnouncementBannerConfigDTO config, String updatedBy);
}
