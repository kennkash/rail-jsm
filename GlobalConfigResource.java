// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/GlobalConfigResource.java

package com.samsungbuilder.jsm.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.samsungbuilder.jsm.dto.GlobalConfigDTO;
import com.samsungbuilder.jsm.dto.ProjectDTO;
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
    private final JiraAuthenticationContext authenticationContext;

    @Inject
    public GlobalConfigResource(
            GlobalConfigService globalConfigService,
            ProjectService projectService,
            @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.globalConfigService = globalConfigService;
        this.projectService = projectService;
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

            // Convert to Map to bypass Jackson serialization issues
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

            // Convert to Map to bypass Jackson serialization issues
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

            // Return only landing page relevant settings
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

