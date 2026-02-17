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

     /**
     * Create a standardized error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }


    /**
     * Get available fields for a project (for JQL table column selection)
     * GET /rest/rail/1.0/projects/{projectKey}/fields
     *
     * Returns system fields and custom fields that can be displayed in the JQL table.
     */
    @GET
    @Path("projects/{projectKey}/fields")
    public Response getProjectFields(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}/fields", projectKey);

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("User not authenticated"))
                        .build();
            }

            var projectManager = ComponentAccessor.getProjectManager();
            var project = projectManager.getProjectObjByKey(projectKey);

            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Project not found: " + projectKey))
                        .build();
            }

            // Check if user can see the project
            var permissionManager = ComponentAccessor.getPermissionManager();
            if (!permissionManager.hasPermission(com.atlassian.jira.security.Permissions.BROWSE, project, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            // Build list of system fields (these are always available)
            List<Map<String, Object>> systemFields = new ArrayList<>();
            systemFields.add(createFieldInfo("key", "Key", "system", false));
            systemFields.add(createFieldInfo("summary", "Summary", "system", false));
            systemFields.add(createFieldInfo("status", "Status", "system", false));
            systemFields.add(createFieldInfo("priority", "Priority", "system", false));
            systemFields.add(createFieldInfo("issueType", "Issue Type", "system", false));
            systemFields.add(createFieldInfo("created", "Created", "system", false));
            systemFields.add(createFieldInfo("updated", "Updated", "system", false));
            systemFields.add(createFieldInfo("dueDate", "Due Date", "system", false));
            systemFields.add(createFieldInfo("reporter", "Reporter", "system", false));
            systemFields.add(createFieldInfo("assignee", "Assignee", "system", false));
            systemFields.add(createFieldInfo("resolution", "Resolution", "system", false));
            systemFields.add(createFieldInfo("labels", "Labels", "system", false));
            systemFields.add(createFieldInfo("components", "Components", "system", false));
            systemFields.add(createFieldInfo("fixVersions", "Fix Versions", "system", false));
            systemFields.add(createFieldInfo("affectedVersions", "Affected Versions", "system", false));

            // Get custom fields for the project
            List<Map<String, Object>> customFields = new ArrayList<>();
            try {
                var customFieldManager = ComponentAccessor.getCustomFieldManager();
                if (customFieldManager != null) {
                    var allCustomFields = customFieldManager.getCustomFieldObjects();
                    for (var cf : allCustomFields) {
                        // Check if custom field is applicable to this project
                        var associatedProjects = cf.getAssociatedProjectObjects();
                        boolean isGlobal = associatedProjects == null || associatedProjects.isEmpty();
                        boolean isForProject = isGlobal || associatedProjects.stream()
                                .anyMatch(p -> p.getKey().equals(projectKey));

                        if (isForProject) {
                            String cfId = cf.getId(); // e.g., "customfield_10001"
                            String cfName = cf.getName();
                            String cfType = cf.getCustomFieldType() != null
                                    ? cf.getCustomFieldType().getName()
                                    : "unknown";

                            Map<String, Object> fieldInfo = createFieldInfo(cfId, cfName, "custom", true);
                            fieldInfo.put("customFieldType", cfType);
                            customFields.add(fieldInfo);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching custom fields for project {}: {}", projectKey, e.getMessage());
                // Continue without custom fields
            }

            Map<String, Object> response = new HashMap<>();
            response.put("projectKey", projectKey);
            response.put("systemFields", systemFields);
            response.put("customFields", customFields);
            response.put("totalFields", systemFields.size() + customFields.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching project fields: projectKey={}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project fields: " + e.getMessage()))
                    .build();
        }
    }
