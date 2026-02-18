/* rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java */
package com.samsungbuilder.jsm.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectCategory;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.issue.fields.CustomField;
import com.atlassian.jira.issue.fields.config.FieldConfigScheme;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.security.PermissionManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.permission.ProjectPermissions;
import com.atlassian.jira.bc.user.search.UserSearchService;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.bc.user.search.UserSearchParams;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.samsungbuilder.jsm.dto.PortalConfigDTO;
import com.samsungbuilder.jsm.dto.PortalHistoryDTO;
import com.samsungbuilder.jsm.dto.ProjectDTO;
import com.samsungbuilder.jsm.dto.RequestTypeDTO;
import com.samsungbuilder.jsm.dto.RequestTypesResponseDTO;
import com.samsungbuilder.jsm.dto.IssueDTO;
import com.samsungbuilder.jsm.service.ProjectService;
import com.samsungbuilder.jsm.service.PortalConfigService;
import com.samsungbuilder.jsm.service.PortalRequestTypeService;
import com.samsungbuilder.jsm.service.IssueService;
import com.samsungbuilder.jsm.dto.IssueSearchResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.StreamingOutput;
import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import com.samsungbuilder.jsm.service.PortalAssetService;
import com.atlassian.jira.issue.attachment.Attachment;

/**
 * REST API resource for Samsung RAIL Portal
 * Provides endpoints for portal functionality including request types, projects, and form submission
 */
@Path("/")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Named
public class RailPortalResource {

    private static final Logger log = LoggerFactory.getLogger(RailPortalResource.class);

    private final PortalRequestTypeService requestTypeService;
    private final ProjectService projectService;
    private final PortalConfigService portalConfigService;
    private final IssueService issueService;
    private final JiraAuthenticationContext authenticationContext;
    private final PortalAssetService portalAssetService;

    // Echo OAuth / API config (shared/public credentials per requirements)
    private static final String ECHO_TOKEN_URL = "https://auth.smartcloud.samsungaustin.com/realms/user_realm/protocol/openid-connect/token";
    private static final String ECHO_CLIENT_ID = "i.castillo2";
    private static final String ECHO_CLIENT_SECRET = "86tyVoBTMzNePmmkjuVphzFLeu2K7aLIhDcL82Wcg";
    private static final String ECHO_BASE_URL = "http://echo.smartcloud.samsungaustin.com";
    private static final String ECHO_DEFAULT_ENDPOINT = "confluence";
    private static final String ECHO_SHARED_KNOX_ID = "i.castillo2";
    private static final int ECHO_CONNECT_TIMEOUT_MS = 30000;
    private static final int ECHO_READ_TIMEOUT_MS = 120000;

    // CloudGPT (Stargate) API config
    private static final String CLOUDGPT_API_URL = "http://api.stargate.smartcloud.samsungaustin.com/v1/chat/completions";
    private static final String CLOUDGPT_BEARER_TOKEN = "i.castillo2:86tyVoBTMzNePmmkjuVphzFLeu2K7aLIhDcL82Wcg";
    private static final String CLOUDGPT_MODEL = "general";
    private static final int CLOUDGPT_CONNECT_TIMEOUT_MS = 30000;
    private static final int CLOUDGPT_READ_TIMEOUT_MS = 60000;

    @Inject
    public RailPortalResource(
            PortalRequestTypeService requestTypeService,
            ProjectService projectService,
            PortalConfigService portalConfigService,
            IssueService issueService,
            PortalAssetService portalAssetService,
            @ComponentImport JiraAuthenticationContext authenticationContext) {
        this.requestTypeService = requestTypeService;
        this.projectService = projectService;
        this.portalConfigService = portalConfigService;
        this.issueService = issueService;
        this.portalAssetService = portalAssetService;
        this.authenticationContext = authenticationContext;
    }

    @GET
    @Path("health")
    public Response health() {
        Map<String, Object> payload = new HashMap<>();
        ApplicationProperties applicationProperties = resolveApplicationProperties();
        payload.put("status", "ok");
        payload.put("baseUrl", applicationProperties != null ? applicationProperties.getBaseUrl() : null);
        payload.put("echoProxy", "/rest/rail/1.0/echo");
        return Response.ok(payload).build();
    }

    /**
     * Echo AI direct proxy - health check
     * GET /rest/rail/1.0/echo
     */
    @GET
    @Path("echo")
    public Response echoHealth() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("endpoint", "echo");
        payload.put("echoService", ECHO_BASE_URL);
        payload.put("defaultEndpoint", ECHO_DEFAULT_ENDPOINT);
        payload.put("streaming", true);
        payload.put("requiresProxy", true);
        payload.put("timestamp", System.currentTimeMillis());
        return Response.ok(payload).build();
    }

    /**
     * Get all portal configurations for projects user can access
     * GET /rest/rail/1.0/portals
     */
    @GET
    @Path("portals")
    public Response getAllPortals() {
        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            com.atlassian.jira.project.ProjectManager projectManager = ComponentAccessor.getProjectManager();
            com.atlassian.jira.security.PermissionManager permissionManager = ComponentAccessor.getPermissionManager();

            if (projectManager == null || permissionManager == null) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(createErrorResponse("Required services not available"))
                        .build();
            }

            List<Map<String, Object>> portals = new ArrayList<>();
            List<com.atlassian.jira.project.Project> allProjects = projectManager.getProjectObjects();

            for (com.atlassian.jira.project.Project project : allProjects) {
                // Only include projects user can browse
                if (permissionManager.hasPermission(
                        com.atlassian.jira.permission.ProjectPermissions.BROWSE_PROJECTS,
                        project,
                        currentUser)) {

                    Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(
                        project.getKey()
                    );

                    Map<String, Object> portalInfo = new HashMap<>();
                    // Project basic info
                    portalInfo.put("projectKey", project.getKey());
                    portalInfo.put("projectName", project.getName());
                    portalInfo.put("portalId", project.getKey().toLowerCase(Locale.ENGLISH) + "-portal");
                    portalInfo.put("isLive", config.map(PortalConfigDTO::isLive).orElse(false));
                    portalInfo.put("hasConfig", config.isPresent());

                    // JSM portal ID for OOTB portal navigation
                    // This is the numeric portal ID used in /plugins/servlet/desk/portal/{id}/
                    // We need this for ALL portals, especially non-live ones that should navigate to OOTB portal
                    String serviceDeskId = null;
                    if (config.isPresent() && config.get().getServiceDeskId() != null) {
                        serviceDeskId = config.get().getServiceDeskId();
                    } else {
                        // For portals without config, try to resolve service desk ID directly
                        try {
                            com.atlassian.servicedesk.api.ServiceDeskManager sdManager =
                                ComponentAccessor.getOSGiComponentInstanceOfType(
                                    com.atlassian.servicedesk.api.ServiceDeskManager.class
                                );
                            if (sdManager != null) {
                                com.atlassian.servicedesk.api.ServiceDesk serviceDesk =
                                    sdManager.getServiceDeskForProject(project);
                                if (serviceDesk != null) {
                                    serviceDeskId = String.valueOf(serviceDesk.getId());
                                }
                            }
                        } catch (Exception e) {
                            log.debug("Could not resolve service desk ID for project {}", project.getKey(), e);
                        }
                    }
                    if (serviceDeskId != null) {
                        portalInfo.put("jsmPortalId", serviceDeskId);
                    }

                    // Optional: project category information for grouping in navigation UI
                    ProjectCategory category = project.getProjectCategory();
                    if (category != null) {
                        if (category.getId() != null) {
                            portalInfo.put("categoryId", category.getId().toString());
                        }
                        portalInfo.put("categoryName", category.getName());
                    }

                    // Optional: project avatar URL for visual cues in navigation
                    try {
                        AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);
                        Avatar avatar = project.getAvatar();
                        if (avatarService != null && avatar != null && avatar.getId() != null) {
                            URI avatarUri = avatarService.getAvatarURL(
                                    null,
                                    avatar.getId().toString(),
                                    Avatar.Size.SMALL
                            );
                            if (avatarUri != null) {
                                portalInfo.put("projectAvatarUrl", avatarUri.toString());
                            }
                        }
                    } catch (Exception avatarEx) {
                        log.debug("Could not resolve avatar for project {}", project.getKey(), avatarEx);
                    }

                    portals.add(portalInfo);
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("portals", portals);
            response.put("count", portals.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching all portals", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching portals: " + e.getMessage()))
                    .build();
        }
    }


    /**
     * Get information about the currently authenticated user.
     * GET /rest/rail/1.0/me
     */
    @GET
    @Path("me")
    public Response getCurrentUser() {
        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("key", currentUser.getKey());
            userInfo.put("displayName", currentUser.getDisplayName());
            userInfo.put("emailAddress", currentUser.getEmailAddress());

            // Best-effort resolution of the user's avatar URL
            try {
                AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);
                if (avatarService != null) {
                    // Use MEDIUM size (typically 48x48) to avoid blurry avatars when displayed at 28px-40px
                    URI avatarUri = avatarService.getAvatarURL(currentUser, currentUser, Avatar.Size.MEDIUM);
                    if (avatarUri != null) {
                        userInfo.put("avatarUrl", avatarUri.toString());
                    }
                }
            } catch (Exception avatarEx) {
                log.debug("Could not resolve avatar URL for current user {}", currentUser.getKey(), avatarEx);
            }

            return Response.ok(userInfo).build();
        } catch (Exception e) {
            log.error("Error fetching current user info", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching current user info: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Search for users by query string
     * GET /rest/rail/1.0/users/search?query={query}&limit={limit}
     */
    @GET
    @Path("users/search")
    public Response searchUsers(
            @QueryParam("query") String query,
            @QueryParam("limit") @DefaultValue("20") int limit) {

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (query == null || query.trim().length() < 2) {
                return Response.ok(Map.of("users", new ArrayList<>(), "count", 0)).build();
            }

            int maxLimit = Math.min(limit, 100);

            UserSearchService userSearchService =
                ComponentAccessor.getComponent(UserSearchService.class);
            UserSearchParams params = new UserSearchParams.Builder()
                    .includeActive(true)
                    .includeInactive(false)
                    .maxResults(maxLimit)
                    .build();
            List<ApplicationUser> foundUsers = userSearchService.findUsers(query.trim(), params);

            List<Map<String, Object>> userList = new ArrayList<>();
            AvatarService avatarService = ComponentAccessor.getComponent(AvatarService.class);

            for (ApplicationUser user : foundUsers) {
                if (!user.isActive()) continue;

                Map<String, Object> userData = new HashMap<>();
                userData.put("username", user.getUsername());
                userData.put("displayName", user.getDisplayName());
                userData.put("emailAddress", user.getEmailAddress());
                userData.put("active", true);

                try {
                    if (avatarService != null) {
                        URI avatarUri = avatarService.getAvatarURL(currentUser, user, Avatar.Size.SMALL);
                        if (avatarUri != null) {
                            userData.put("avatarUrl", avatarUri.toString());
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not resolve avatar for user {}", user.getKey(), e);
                }

                userList.add(userData);
            }

            return Response.ok(Map.of("users", userList, "count", userList.size())).build();

        } catch (Exception e) {
            log.error("Error searching users", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error searching users: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Search for groups by query string
     * GET /rest/rail/1.0/groups/search?query={query}&limit={limit}
     */
    @GET
    @Path("groups/search")
    public Response searchGroups(
            @QueryParam("query") String query,
            @QueryParam("limit") @DefaultValue("20") int limit) {

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("Authentication required"))
                        .build();
            }

            if (query == null || query.trim().length() < 2) {
                return Response.ok(Map.of("groups", new ArrayList<>(), "count", 0)).build();
            }

            int maxLimit = Math.min(limit, 100);
            GroupManager groupManager = ComponentAccessor.getGroupManager();
            java.util.Collection<com.atlassian.crowd.embedded.api.Group> allGroups = groupManager.getAllGroups();

            String queryLower = query.trim().toLowerCase();
            List<Map<String, Object>> groupList = new ArrayList<>();

            for (com.atlassian.crowd.embedded.api.Group group : allGroups) {
                if (group.getName().toLowerCase().contains(queryLower)) {
                    Map<String, Object> groupData = new HashMap<>();
                    groupData.put("name", group.getName());

                    try {
                        java.util.Collection<ApplicationUser> members = groupManager.getUsersInGroup(group);
                        groupData.put("memberCount", members != null ? members.size() : 0);
                    } catch (Exception e) {
                        groupData.put("memberCount", 0);
                    }

                    groupList.add(groupData);

                    if (groupList.size() >= maxLimit) break;
                }
            }

            groupList.sort((g1, g2) ->
                ((String) g1.get("name")).compareToIgnoreCase((String) g2.get("name"))
            );

            return Response.ok(Map.of("groups", groupList, "count", groupList.size())).build();

        } catch (Exception e) {
            log.error("Error searching groups", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error searching groups: " + e.getMessage()))
                    .build();
        }
    }

    // ==================== TEMP-DEBUG: New Portal Data Endpoints (Map-wrapped) ====================

    /**
     * TEMP-DEBUG: Get portal data using Map wrapping (proven working pattern from diagnostic endpoint)
     * GET /rest/rail/1.0/portal-data/{projectKey}
     */
    @GET
    @Path("portal-data/{projectKey}")
    public Response getPortalDataWrapped(@PathParam("projectKey") String projectKey) {
        log.info("TEMP-DEBUG: GET /portal-data/{} - Map-wrapped endpoint", projectKey);

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            Map<String, Object> response = new HashMap<>();
            Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(projectKey);

            if (config.isPresent()) {
                PortalConfigDTO dto = config.get();

                // FIX: Manually convert DTO to Map to bypass Jackson serialization issues
                Map<String, Object> configMap = new HashMap<>();
                configMap.put("projectKey", dto.getProjectKey());
                configMap.put("projectName", dto.getProjectName());
                configMap.put("portalId", dto.getPortalId());
                configMap.put("portalTitle", dto.getPortalTitle());
                configMap.put("live", dto.isLive());
                configMap.put("components", dto.getComponents());
                configMap.put("requestTypeGroups", dto.getRequestTypeGroups());
                configMap.put("updatedAt", dto.getUpdatedAt());

                response.put("success", true);
                response.put("source", "database");
                response.put("config", configMap); // Return Map instead of DTO
                response.put("debug", Map.of(
                    "hasComponents", dto.getComponents() != null,
                    "componentCount", dto.getComponents() != null ? dto.getComponents().size() : 0,
                    "projectKey", dto.getProjectKey(),
                    "portalId", dto.getPortalId(),
                    "message", "DTO converted to Map to bypass Jackson serialization"
                ));
                log.info("  Found config with {} components, converted to Map", dto.getComponents() != null ? dto.getComponents().size() : 0);
            } else {
                PortalConfigDTO sample = buildSamplePortalConfig(projectKey, null);
                portalConfigService.savePortalConfig(projectKey, sample);

                // Convert sample to Map too
                Map<String, Object> configMap = new HashMap<>();
                configMap.put("projectKey", sample.getProjectKey());
                configMap.put("projectName", sample.getProjectName());
                configMap.put("portalId", sample.getPortalId());
                configMap.put("portalTitle", sample.getPortalTitle());
                configMap.put("live", sample.isLive());
                configMap.put("components", sample.getComponents());
                configMap.put("requestTypeGroups", sample.getRequestTypeGroups());
                configMap.put("updatedAt", sample.getUpdatedAt());

                response.put("success", true);
                response.put("source", "generated-sample");
                response.put("config", configMap);
                response.put("debug", Map.of(
                    "componentCount", sample.getComponents().size(),
                    "message", "No config found, created sample and converted to Map"
                ));
                log.info("  Created sample config with {} components, converted to Map", sample.getComponents().size());
            }

            response.put("timestamp", System.currentTimeMillis());
            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("TEMP-DEBUG: Error in portal-data endpoint", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }

    /**
     * TEMP-DEBUG: Save portal data using Map wrapping
     * POST /rest/rail/1.0/portal-data/{projectKey}
     */
    @POST
    @Path("portal-data/{projectKey}")
    public Response savePortalDataWrapped(@PathParam("projectKey") String projectKey, PortalConfigDTO config) {
        log.info("TEMP-DEBUG: POST /portal-data/{} - Map-wrapped save", projectKey);
        log.info("  Received components: {}", config.getComponents() != null ? config.getComponents().size() : 0);

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            PortalConfigDTO saved = portalConfigService.savePortalConfig(projectKey, config);

            // FIX: Manually convert DTO to Map to bypass Jackson serialization issues
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("projectKey", saved.getProjectKey());
            configMap.put("projectName", saved.getProjectName());
            configMap.put("portalId", saved.getPortalId());
            configMap.put("portalTitle", saved.getPortalTitle());
            configMap.put("live", saved.isLive());
            configMap.put("components", saved.getComponents());
            configMap.put("requestTypeGroups", saved.getRequestTypeGroups());
            configMap.put("updatedAt", saved.getUpdatedAt());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("config", configMap); // Return Map instead of DTO
            response.put("debug", Map.of(
                "receivedComponents", config.getComponents() != null ? config.getComponents().size() : 0,
                "savedComponents", saved.getComponents() != null ? saved.getComponents().size() : 0,
                "projectKey", saved.getProjectKey(),
                "portalId", saved.getPortalId(),
                "message", "DTO converted to Map to bypass Jackson serialization"
            ));
            response.put("timestamp", System.currentTimeMillis());

            log.info("  Saved successfully with {} components, converted to Map", saved.getComponents() != null ? saved.getComponents().size() : 0);
            log.info("  Response map keys: {}", response.keySet());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("TEMP-DEBUG: Error saving portal data", e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            error.put("timestamp", System.currentTimeMillis());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }

    // ==================== Portal Config Endpoints ====================

    /**
     * Fetch portal configuration for a project key.
     *
     * FIX: Returns Map instead of DTO to bypass JAX-RS Jackson serialization issues.
     * JAX-RS uses a different Jackson ObjectMapper than PortalConfigService, which
     * strips all fields except 'live' from the response. Converting to Map works around this.
     */
    @GET
    @Path("portals/project/{projectKey}")
    public Response getPortalConfigForProject(@PathParam("projectKey") String projectKey) {
        log.info("GET /portals/project/{} - Fetching portal configuration", projectKey);

        Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(projectKey);

        if (config.isPresent()) {
            PortalConfigDTO dto = config.get();
            log.info("  Config exists - hasComponents: {}, componentsSize: {}",
                     dto.getComponents() != null,
                     dto.getComponents() != null ? dto.getComponents().size() : 0);

            // DIAGNOSTIC: Log the DTO before serialization
            log.info("  DTO before serialization - projectKey: {}, portalId: {}, live: {}, componentsSize: {}",
                     dto.getProjectKey(), dto.getPortalId(), dto.isLive(),
                     dto.getComponents() != null ? dto.getComponents().size() : "null");

            // FIX: Convert DTO to Map to bypass Jackson serialization issues
            Map<String, Object> configMap = convertDtoToMap(dto);

            log.info("  Returning Map with {} components",
                     ((List<?>) configMap.get("components")).size());

            return Response.ok(configMap).build();
        }

        log.info("  No config found - creating sample config");
        PortalConfigDTO sample = buildSamplePortalConfig(projectKey, null);
        portalConfigService.savePortalConfig(projectKey, sample);
        log.info("  Sample config created and saved with {} components", sample.getComponents().size());

        // FIX: Convert sample to Map as well
        Map<String, Object> sampleMap = convertDtoToMap(sample);
        return Response.ok(sampleMap).build();
    }

    /**
     * Diagnostic endpoint to check raw portal data without serialization issues
     * GET /rest/rail/1.0/portals/project/{projectKey}/diagnostic
     */
    @GET
    @Path("portals/project/{projectKey}/diagnostic")
    public Response getPortalConfigDiagnostic(@PathParam("projectKey") String projectKey) {
        log.info("GET /portals/project/{}/diagnostic - Diagnostic check", projectKey);

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        Map<String, Object> diagnostic = new HashMap<>();
        diagnostic.put("projectKey", projectKey);
        diagnostic.put("timestamp", System.currentTimeMillis());

        try {
            Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(projectKey);

            if (config.isPresent()) {
                PortalConfigDTO dto = config.get();
                diagnostic.put("configExists", true);
                diagnostic.put("dtoProjectKey", dto.getProjectKey());
                diagnostic.put("dtoPortalId", dto.getPortalId());
                diagnostic.put("dtoLive", dto.isLive());
                diagnostic.put("dtoComponentsNull", dto.getComponents() == null);
                diagnostic.put("dtoComponentsSize", dto.getComponents() != null ? dto.getComponents().size() : 0);
                diagnostic.put("dtoRequestTypeGroupsNull", dto.getRequestTypeGroups() == null);
                diagnostic.put("dtoRequestTypeGroupsSize", dto.getRequestTypeGroups() != null ? dto.getRequestTypeGroups().size() : 0);

                // Wrap the actual DTO in a Map to bypass any serialization issues
                Map<String, Object> wrappedConfig = new HashMap<>();
                wrappedConfig.put("projectKey", dto.getProjectKey());
                wrappedConfig.put("projectName", dto.getProjectName());
                wrappedConfig.put("portalId", dto.getPortalId());
                wrappedConfig.put("portalTitle", dto.getPortalTitle());
                wrappedConfig.put("live", dto.isLive());
                wrappedConfig.put("components", dto.getComponents());
                wrappedConfig.put("requestTypeGroups", dto.getRequestTypeGroups());
                wrappedConfig.put("updatedAt", dto.getUpdatedAt());

                diagnostic.put("config", wrappedConfig);
            } else {
                diagnostic.put("configExists", false);
                diagnostic.put("message", "No config found in database");
            }

            return Response.ok(diagnostic).build();
        } catch (Exception e) {
            diagnostic.put("error", e.getMessage());
            diagnostic.put("errorClass", e.getClass().getName());
            log.error("Diagnostic endpoint error for project: " + projectKey, e);
            return Response.ok(diagnostic).build();
        }
    }

    @GET
    @Path("portals/{keyOrId}")
    public Response getPortalConfigFlexible(@PathParam("keyOrId") String keyOrId) {
        if (keyOrId == null || keyOrId.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Portal identifier is required"))
                    .build();
        }

        String trimmed = keyOrId.trim();
        if (trimmed.toLowerCase(Locale.ENGLISH).endsWith("-portal")) {
            return getPortalConfigByIdInternal(trimmed);
        }
        return getPortalConfigForProject(trimmed);
    }

    /**
     * Toggle portal live flag.
     */
    @POST
    @Path("portals/project/{projectKey}/live")
    public Response setPortalLive(
            @PathParam("projectKey") String projectKey,
            Map<String, Object> body
    ) {
        boolean live = body != null && Boolean.TRUE.equals(body.get("live"));

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            PortalConfigDTO updated = portalConfigService.updateLiveState(projectKey, live);
            // FIX: Convert to Map to bypass Jackson serialization issues
            return Response.ok(convertDtoToMap(updated)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(ex.getMessage()))
                    .build();
        }
    }


    @GET
    @Path("portals/by-id/{portalId}")
    public Response getPortalConfigById(@PathParam("portalId") String portalId) {
        return getPortalConfigByIdInternal(portalId);
    }

    private Response getPortalConfigByIdInternal(String portalId) {
        Optional<PortalConfigDTO> config = portalConfigService.getPortalConfigByPortalId(portalId);
        if (config.isPresent()) {
            PortalConfigDTO dto = config.get();
            // FIX: Convert to Map to bypass Jackson serialization issues
            return Response.ok(convertDtoToMap(dto)).build();
        }

        PortalConfigDTO sample = buildSamplePortalConfig(null, portalId);
        if (sample.getProjectKey() != null) {
            portalConfigService.savePortalConfig(sample.getProjectKey(), sample);
        }
        // FIX: Convert to Map to bypass Jackson serialization issues
        return Response.ok(convertDtoToMap(sample)).build();
    }

    @POST
    @Path("portals/project/{projectKey}")
    public Response savePortalConfig(@PathParam("projectKey") String projectKey, PortalConfigDTO config) {
        if (config == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Portal payload is required"))
                    .build();
        }

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            log.info("POST /portals/project/{} - Saving portal config", projectKey);
            log.info("  Received components count: {}", config.getComponents() != null ? config.getComponents().size() : 0);

            PortalConfigDTO saved = portalConfigService.savePortalConfig(projectKey, config);

            log.info("  Saved successfully - components count: {}", saved.getComponents() != null ? saved.getComponents().size() : 0);
            log.info("  Returning saved config with projectKey: {}, portalId: {}", saved.getProjectKey(), saved.getPortalId());

            // FIX: Convert to Map to bypass Jackson serialization issues
            return Response.ok(convertDtoToMap(saved)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(ex.getMessage()))
                    .build();
        }
    }

    /**
     * Upload an image/file to be used by portal components. Stored as an attachment
     * on a hidden project issue and served back via a proxy endpoint.
     */
    @POST
    @Path("portals/project/{projectKey}/assets")
    @Consumes(MediaType.WILDCARD)
    public Response uploadPortalAsset(
            @PathParam("projectKey") String projectKey,
            @QueryParam("filename") String filename,
            @QueryParam("contentType") @DefaultValue("application/octet-stream") String contentType,
            InputStream body) {

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("No file provided"))
                    .build();
        }

        String safeName = filename != null ? filename.trim() : "";
        if (safeName.isEmpty()) {
            safeName = "upload.bin";
        }

        byte[] payload;
        try {
            payload = readLimited(body, 5 * 1024 * 1024); // 5 MB limit
        } catch (IllegalStateException sizeEx) {
            return Response.status(413)
                    .entity(createErrorResponse(sizeEx.getMessage()))
                    .build();
        } catch (Exception e) {
            log.error("Failed to read upload stream for project {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Unable to read upload"))
                    .build();
        }

        if (payload.length == 0) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Empty file"))
                    .build();
        }

        try {
            Attachment attachment = portalAssetService.upload(projectKey, safeName, contentType, payload);
            String encodedKey = projectKey;
            try {
                encodedKey = URLEncoder.encode(projectKey, StandardCharsets.UTF_8.toString());
            } catch (Exception ignore) {
                // Fallback to raw project key if encoding somehow fails
            }
            String downloadUrl = String.format(
                    "/rest/rail/1.0/portals/project/%s/assets/%s",
                    encodedKey,
                    attachment.getId()
            );

            Map<String, Object> response = new HashMap<>();
            response.put("attachmentId", attachment.getId());
            response.put("fileName", attachment.getFilename());
            response.put("size", attachment.getFilesize());
            response.put("contentType", attachment.getMimetype());
            response.put("downloadUrl", downloadUrl);
            return Response.ok(response).build();
        } catch (Exception e) {
            log.error("Upload failed for project {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Unable to store file: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Stream a previously uploaded asset back to the browser.
     */
    @GET
    @Path("portals/project/{projectKey}/assets/{attachmentId}")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response downloadPortalAsset(
            @PathParam("projectKey") String projectKey,
            @PathParam("attachmentId") long attachmentId) {

        ApplicationUser user = authenticationContext.getLoggedInUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(createErrorResponse("Authentication required"))
                    .build();
        }

        Optional<Attachment> attachmentOpt = portalAssetService.getAttachment(attachmentId, projectKey);
        if (!attachmentOpt.isPresent()) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse("Attachment not found"))
                    .build();
        }

        Attachment attachment = attachmentOpt.get();

        StreamingOutput stream = output -> {
            try (InputStream in = portalAssetService.openAttachmentStream(attachment)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = in.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }
            }
        };

        return Response.ok(stream)
                .header("Content-Type", attachment.getMimetype() != null ? attachment.getMimetype() : MediaType.APPLICATION_OCTET_STREAM)
                .header("Content-Disposition", "inline; filename=\"" + attachment.getFilename() + "\"")
                .header("Cache-Control", "private, max-age=3600")
                .build();
    }


    // ==================== Portal History Endpoints ====================

    /**
     * Get portal history for a project
     * GET /rest/rail/1.0/portals/project/{projectKey}/history
     */
    @GET
    @Path("portals/project/{projectKey}/history")
    public Response getPortalHistory(@PathParam("projectKey") String projectKey) {

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            Optional<PortalHistoryDTO> history = portalConfigService.getPortalHistory(projectKey);
            if (history.isPresent()) {
                return Response.ok(history.get()).build();
            }

            // Return empty history if none exists
            PortalHistoryDTO emptyHistory = new PortalHistoryDTO();
            emptyHistory.setProjectKey(projectKey);
            emptyHistory.setUpdatedAt(System.currentTimeMillis());
            return Response.ok(emptyHistory).build();

        } catch (Exception ex) {
            log.error("Error fetching portal history for project: " + projectKey, ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching portal history: " + ex.getMessage()))
                    .build();
        }
    }

    /**
     * Save portal history for a project
     * POST /rest/rail/1.0/portals/project/{projectKey}/history
     */
    @POST
    @Path("portals/project/{projectKey}/history")
    public Response savePortalHistory(@PathParam("projectKey") String projectKey, PortalHistoryDTO history) {
        if (history == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("History payload is required"))
                    .build();
        }

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            portalConfigService.savePortalHistory(projectKey, history);
            // Frontend only cares about success/failure, not the payload
            // Use 204 No Content to avoid serializing PortalHistoryDTO
            return Response.noContent().build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(ex.getMessage()))
                    .build();
        } catch (Exception ex) {
            log.error("Error saving portal history for project: " + projectKey, ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error saving portal history: " + ex.getMessage()))
                    .build();
        }
    }

    /**
     * Delete portal history for a project
     * DELETE /rest/rail/1.0/portals/project/{projectKey}/history
     */
    @DELETE
    @Path("portals/project/{projectKey}/history")
    public Response deletePortalHistory(@PathParam("projectKey") String projectKey) {
        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            portalConfigService.deletePortalHistory(projectKey);
            return Response.noContent().build();
        } catch (Exception ex) {
            log.error("Error deleting portal history for project: " + projectKey, ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error deleting portal history: " + ex.getMessage()))
                    .build();
        }
