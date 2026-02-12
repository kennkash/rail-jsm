/* rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/RailPortalResource.java */

package com.samsungbuilder.jsm.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.avatar.Avatar;
import com.atlassian.jira.avatar.AvatarService;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectCategory;
import com.atlassian.jira.project.ProjectManager;
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

   // ==================== Issue Search Endpoints ====================

    /**
     * Search issues using JQL query with optional server-side search and filter.
     *
     * Server-side search/filter enables searching across the ENTIRE result set,
     * not just the current page. This fixes the issue where search only worked
     * on visible issues.
     *
     * GET /rest/rail/1.0/issues/search?jql=query&start=0&limit=25&search=term&status=Open,Done&priority=High
     *
     * @param jqlQuery The JQL query (required)
     * @param startIndex Pagination start index (default: 0)
     * @param pageSize Number of results per page (default: 25)
     * @param searchTerm Optional text search (searches key, summary, description)
     * @param statusFilter Optional comma-separated status names to filter by
     * @param priorityFilter Optional comma-separated priority names to filter by
     */
    @GET
    @Path("issues/search")
    public Response searchIssues(
            @QueryParam("jql") String jqlQuery,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize,
            @QueryParam("search") String searchTerm,
            @QueryParam("status") String statusFilter,
            @QueryParam("priority") String priorityFilter) {
        log.debug("GET /issues/search?jql={}&start={}&limit={}&search={}&status={}&priority={}",
                  jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);

        if (jqlQuery == null || jqlQuery.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("JQL query parameter is required"))
                    .build();
        }

        try {
            IssueSearchResponseDTO response = issueService.searchIssues(
                    jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid JQL query: {}", jqlQuery, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Invalid JQL query: " + e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Error searching issues with JQL: {}", jqlQuery, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Issue search failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get issues for a specific project (current user's issues)
     * GET /rest/rail/1.0/projects/{projectKey}/issues?start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues")
    public Response getProjectIssues(
            @PathParam("projectKey") String projectKey,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues?start={}&limit={}", projectKey, startIndex, pageSize);

        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchProjectIssues(projectKey, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (Exception e) {
            log.error("Error fetching issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project issues: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all issues for a specific project (that user can see)
     * GET /rest/rail/1.0/projects/{projectKey}/issues/all?start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues/all")
    public Response getAllProjectIssues(
            @PathParam("projectKey") String projectKey,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues/all?start={}&limit={}", projectKey, startIndex, pageSize);

        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchAllProjectIssues(projectKey, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (Exception e) {
            log.error("Error fetching all issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project issues: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get issues for a specific project with additional filter
     * GET /rest/rail/1.0/projects/{projectKey}/issues/filter?filter=status=Open&start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues/filter")
    public Response getProjectIssuesWithFilter(
            @PathParam("projectKey") String projectKey,
            @QueryParam("filter") String additionalFilter,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues/filter?filter={}&start={}&limit={}", projectKey, additionalFilter, startIndex, pageSize);

        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchProjectIssuesWithFilter(projectKey, additionalFilter, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter for project {}: {}", projectKey, additionalFilter, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Invalid filter: " + e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Error fetching filtered issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching filtered project issues: " + e.getMessage()))
                    .build();
        }
    }
