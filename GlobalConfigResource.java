// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/GlobalConfigResource.java

package com.samsungbuilder.jsm.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;
import com.samsungbuilder.jsm.dto.GlobalConfigDTO;
import com.samsungbuilder.jsm.dto.ProjectDTO;
import com.samsungbuilder.jsm.service.AnnouncementBannerService;
import com.samsungbuilder.jsm.service.GlobalConfigService;
import com.samsungbuilder.jsm.service.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API resource for Global Admin Configuration
 * Provides endpoints for managing global RAIL Portal settings
 * Only accessible by Jira administrators
 */
@Path("/admin")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Named
public class GlobalConfigResource {

    private static final Logger log = LoggerFactory.getLogger(GlobalConfigResource.class);
    private static final String ADMIN_GROUP = "jira-administrators";

    private final GlobalConfigService globalConfigService;
    private final ProjectService projectService;
    private final AnnouncementBannerService announcementBannerService;
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public GlobalConfigResource(
            GlobalConfigService globalConfigService,
            ProjectService projectService,
            AnnouncementBannerService announcementBannerService,
            @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.globalConfigService = globalConfigService;
        this.projectService = projectService;
        this.announcementBannerService = announcementBannerService;
        this.authenticationContext = authenticationContext;
    }

    /**
     * Check if current user is a Jira administrator
     */
    private boolean isAdmin(ApplicationUser user) {
        if (user == null) {
            return false;
        }
        GroupManager groupManager = ComponentAccessor.getGroupManager();
        var adminGroup = groupManager.getGroup(ADMIN_GROUP);
        return adminGroup != null && groupManager.isUserInGroup(user, adminGroup);
    }

    /**
     * Create error response map
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    /**
     * Convert announcement banner config to Map to bypass Jackson serialization issues
     */
    private Map<String, Object> toAnnouncementBannerMap(AnnouncementBannerConfigDTO config) {
        Map<String, Object> map = new HashMap<>();
        map.put("enabled", config.isEnabled());
        map.put("title", config.getTitle());
        map.put("message", config.getMessage());
        map.put("icon", config.getIcon());
        map.put("backgroundColor", config.getBackgroundColor());
        map.put("borderColor", config.getBorderColor());
        map.put("textColor", config.getTextColor());
        map.put("updatedBy", config.getUpdatedBy());
        map.put("updatedAtEpochMs", config.getUpdatedAtEpochMs());
        return map;
    }

    /**
     * Get global configuration
     * GET /rest/rail/1.0/admin/config
     */
    @GET
    @Path("config")
    public Response getGlobalConfig() {
        log.info("GET /admin/config - Fetching global configuration");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (!isAdmin(currentUser)) {
                log.warn("Non-admin user {} attempted to access global config", currentUser.getUsername());
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Administrator access required"))
                        .build();
            }

            GlobalConfigDTO config = globalConfigService.getGlobalConfig();

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("restrictedProjectKeys", config.getRestrictedProjectKeys());
            configMap.put("echoMasterPrompt", config.getEchoMasterPrompt());
            configMap.put("echoEnabled", config.isEchoEnabled());
            configMap.put("echoMaxTokens", config.getEchoMaxTokens());
            configMap.put("echoDefaultModel", config.getEchoDefaultModel());

            // General Settings
            configMap.put("portalTitle", config.getPortalTitle());
            configMap.put("portalSubtitle", config.getPortalSubtitle());
            configMap.put("portalLogoUrl", config.getPortalLogoUrl());
            configMap.put("supportEmail", config.getSupportEmail());
            configMap.put("supportUrl", config.getSupportUrl());
            configMap.put("defaultTheme", config.getDefaultTheme());
            configMap.put("showPoweredByRail", config.isShowPoweredByRail());
            configMap.put("enableRequestSearch", config.isEnableRequestSearch());
            configMap.put("enableRecentPortals", config.isEnableRecentPortals());
            configMap.put("maxRecentPortals", config.getMaxRecentPortals());
            configMap.put("sessionTimeoutMinutes", config.getSessionTimeoutMinutes());
            configMap.put("enableAnalytics", config.isEnableAnalytics());

            // Metadata
            configMap.put("updatedAt", config.getUpdatedAt());
            configMap.put("updatedBy", config.getUpdatedBy());

            log.info("Returning global config with {} restricted projects",
                    config.getRestrictedProjectKeys().size());

            return Response.ok(configMap).build();

        } catch (Exception e) {
            log.error("Error fetching global config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching global config: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Save global configuration
     * PUT /rest/rail/1.0/admin/config
     */
    @PUT
    @Path("config")
    public Response saveGlobalConfig(GlobalConfigDTO config) {
        log.info("PUT /admin/config - Saving global configuration");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (!isAdmin(currentUser)) {
                log.warn("Non-admin user {} attempted to save global config", currentUser.getUsername());
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Administrator access required"))
                        .build();
            }

            if (config == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("Configuration payload is required"))
                        .build();
            }

            GlobalConfigDTO saved = globalConfigService.saveGlobalConfig(config, currentUser.getUsername());

            Map<String, Object> configMap = new HashMap<>();
            configMap.put("restrictedProjectKeys", saved.getRestrictedProjectKeys());
            configMap.put("echoMasterPrompt", saved.getEchoMasterPrompt());
            configMap.put("echoEnabled", saved.isEchoEnabled());
            configMap.put("echoMaxTokens", saved.getEchoMaxTokens());
            configMap.put("echoDefaultModel", saved.getEchoDefaultModel());

            // General Settings
            configMap.put("portalTitle", saved.getPortalTitle());
            configMap.put("portalSubtitle", saved.getPortalSubtitle());
            configMap.put("portalLogoUrl", saved.getPortalLogoUrl());
            configMap.put("supportEmail", saved.getSupportEmail());
            configMap.put("supportUrl", saved.getSupportUrl());
            configMap.put("defaultTheme", saved.getDefaultTheme());
            configMap.put("showPoweredByRail", saved.isShowPoweredByRail());
            configMap.put("enableRequestSearch", saved.isEnableRequestSearch());
            configMap.put("enableRecentPortals", saved.isEnableRecentPortals());
            configMap.put("maxRecentPortals", saved.getMaxRecentPortals());
            configMap.put("sessionTimeoutMinutes", saved.getSessionTimeoutMinutes());
            configMap.put("enableAnalytics", saved.isEnableAnalytics());

            // Metadata
            configMap.put("updatedAt", saved.getUpdatedAt());
            configMap.put("updatedBy", saved.getUpdatedBy());

            log.info("Saved global config by user {}", currentUser.getUsername());

            return Response.ok(configMap).build();

        } catch (Exception e) {
            log.error("Error saving global config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error saving global config: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get announcement banner config for administrators
     * GET /rest/rail/1.0/admin/announcement-banner
     */
    @GET
    @Path("announcement-banner")
    public Response getAnnouncementBannerAdmin() {
        log.info("GET /admin/announcement-banner - Fetching announcement banner config");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (!isAdmin(currentUser)) {
                log.warn("Non-admin user {} attempted to access announcement banner config", currentUser.getUsername());
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Administrator access required"))
                        .build();
            }

            AnnouncementBannerConfigDTO config = announcementBannerService.getConfig();
            return Response.ok(toAnnouncementBannerMap(config)).build();

        } catch (Exception e) {
            log.error("Error fetching announcement banner config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching announcement banner config: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Save announcement banner config for administrators
     * PUT /rest/rail/1.0/admin/announcement-banner
     */
    @PUT
    @Path("announcement-banner")
    public Response saveAnnouncementBannerAdmin(AnnouncementBannerConfigDTO config) {
        log.info("PUT /admin/announcement-banner - Saving announcement banner config");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (!isAdmin(currentUser)) {
                log.warn("Non-admin user {} attempted to save announcement banner config", currentUser.getUsername());
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Administrator access required"))
                        .build();
            }

            if (config == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(createErrorResponse("Announcement banner payload is required"))
                        .build();
            }

            AnnouncementBannerConfigDTO saved =
                    announcementBannerService.saveConfig(config, currentUser.getUsername());

            log.info("Saved announcement banner config by user {}", currentUser.getUsername());
            return Response.ok(toAnnouncementBannerMap(saved)).build();

        } catch (Exception e) {
            log.error("Error saving announcement banner config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error saving announcement banner config: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all projects for the project picker
     * GET /rest/rail/1.0/admin/projects
     */
    @GET
    @Path("projects")
    public Response getAllProjects() {
        log.debug("GET /admin/projects - Fetching all projects for admin");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (!isAdmin(currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Administrator access required"))
                        .build();
            }

            List<ProjectDTO> projects = projectService.getAllServiceDeskProjects(currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("projects", projects);
            response.put("count", projects.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching projects for admin", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching projects: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Check if a project is restricted
     * GET /rest/rail/1.0/admin/projects/{projectKey}/restricted
     */
    @GET
    @Path("projects/{projectKey}/restricted")
    public Response isProjectRestricted(@PathParam("projectKey") String projectKey) {
        log.debug("GET /admin/projects/{}/restricted", projectKey);

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            boolean restricted = globalConfigService.isProjectRestricted(projectKey);

            Map<String, Object> response = new HashMap<>();
            response.put("projectKey", projectKey);
            response.put("restricted", restricted);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error checking project restriction: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error checking restriction: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get Echo AI global settings (for use by Echo AI integration)
     * GET /rest/rail/1.0/admin/echo
     */
    @GET
    @Path("echo")
    public Response getEchoSettings() {
        log.debug("GET /admin/echo - Fetching Echo AI settings");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            GlobalConfigDTO config = globalConfigService.getGlobalConfig();

            Map<String, Object> echoSettings = new HashMap<>();
            echoSettings.put("enabled", config.isEchoEnabled());
            echoSettings.put("masterPrompt", config.getEchoMasterPrompt());
            echoSettings.put("maxTokens", config.getEchoMaxTokens());
            echoSettings.put("defaultModel", config.getEchoDefaultModel());

            return Response.ok(echoSettings).build();

        } catch (Exception e) {
            log.error("Error fetching Echo settings", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching Echo settings: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Health check for admin API
     * GET /rest/rail/1.0/admin/health
     */
    @GET
    @Path("health")
    public Response health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "ok");
        response.put("endpoint", "admin");
        response.put("timestamp", System.currentTimeMillis());
        return Response.ok(response).build();
    }

    /**
     * Get landing page settings (public endpoint for customer portal)
     * GET /rest/rail/1.0/admin/landing-config
     *
     * This endpoint is accessible to any authenticated user and returns
     * only the settings needed for the customer-facing landing page.
     */
    @GET
    @Path("landing-config")
    public Response getLandingPageConfig() {
        log.debug("GET /admin/landing-config - Fetching landing page configuration");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            GlobalConfigDTO config = globalConfigService.getGlobalConfig();

            Map<String, Object> landingConfig = new HashMap<>();
            // Branding
            landingConfig.put("portalTitle", config.getPortalTitle());
            landingConfig.put("portalSubtitle", config.getPortalSubtitle());
            landingConfig.put("portalLogoUrl", config.getPortalLogoUrl());
            landingConfig.put("showPoweredByRail", config.isShowPoweredByRail());
            // Feature toggles
            landingConfig.put("enableRecentPortals", config.isEnableRecentPortals());
            landingConfig.put("maxRecentPortals", config.getMaxRecentPortals());
            landingConfig.put("enableRequestSearch", config.isEnableRequestSearch());
            // Support
            landingConfig.put("supportEmail", config.getSupportEmail());
            landingConfig.put("supportUrl", config.getSupportUrl());

            return Response.ok(landingConfig).build();

        } catch (Exception e) {
            log.error("Error fetching landing page config", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching landing page config: " + e.getMessage()))
                    .build();
        }
    }
}


/**
 * Get announcement banner config for authenticated landing-page users
 * GET /rest/rail/1.0/announcement-banner
 */
@GET
@Path("announcement-banner")
public Response getAnnouncementBanner() {
    log.debug("GET /announcement-banner - Fetching public announcement banner config");

    try {
        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        if (currentUser == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(createErrorResponse("Authentication required"))
                    .build();
        }

        AnnouncementBannerConfigDTO config = announcementBannerService.getConfig();

        Map<String, Object> response = new HashMap<>();
        response.put("enabled", config.isEnabled());
        response.put("title", config.getTitle());
        response.put("message", config.getMessage());
        response.put("icon", config.getIcon());
        response.put("backgroundColor", config.getBackgroundColor());
        response.put("borderColor", config.getBorderColor());
        response.put("textColor", config.getTextColor());
        response.put("updatedBy", config.getUpdatedBy());
        response.put("updatedAtEpochMs", config.getUpdatedAtEpochMs());

        return Response.ok(response).build();

    } catch (Exception e) {
        log.error("Error fetching public announcement banner config", e);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(createErrorResponse("Error fetching announcement banner config: " + e.getMessage()))
                .build();
    }
}


import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;
import com.samsungbuilder.jsm.service.AnnouncementBannerService;

private final AnnouncementBannerService announcementBannerService;


@Inject
public RailPortalResource(
        PortalRequestTypeService requestTypeService,
        ProjectService projectService,
        PortalConfigService portalConfigService,
        IssueService issueService,
        PortalAssetService portalAssetService,
        AnnouncementBannerService announcementBannerService,
        @ComponentImport JiraAuthenticationContext authenticationContext) {
    this.requestTypeService = requestTypeService;
    this.projectService = projectService;
    this.portalConfigService = portalConfigService;
    this.issueService = issueService;
    this.portalAssetService = portalAssetService;
    this.announcementBannerService = announcementBannerService;
    this.authenticationContext = authenticationContext;
}



const API_BASE = "/rest/rail/1.0";

export async function fetchAnnouncementBanner() {
  return fetch(`${API_BASE}/announcement-banner`, {
    credentials: "same-origin",
  });
}

export async function fetchAnnouncementBannerAdmin() {
  return fetch(`${API_BASE}/admin/announcement-banner`, {
    credentials: "same-origin",
  });
}

export async function saveAnnouncementBanner(payload: AnnouncementBannerConfig) {
  return fetch(`${API_BASE}/admin/announcement-banner`, {
    method: "PUT",
    credentials: "same-origin",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(payload),
  });
}

