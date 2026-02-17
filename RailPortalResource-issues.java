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

    // ==================== Issue Search Endpoints ====================

    /**
     * Search issues using JQL query with optional server-side search and filter.
     *
     * GET /rest/rail/1.0/issues/search?jql=query&start=0&limit=25
     *   &search=term&status=Open,Done&priority=High
     *   &sortField=status&sortDir=asc
     *   &facets=true
     */
    @GET
    @Path("issues/search")
    public Response searchIssues(
            @QueryParam("jql") String jqlQuery,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize,
            @QueryParam("search") String searchTerm,
            @QueryParam("status") String statusFilter,
            @QueryParam("priority") String priorityFilter,
            // NEW: server-side sorting
            @QueryParam("sortField") String sortField,
            @QueryParam("sortDir") @DefaultValue("asc") String sortDir,
            // NEW: facets for dropdowns
            @QueryParam("facets") @DefaultValue("false") boolean includeFacets
    ) {
        log.debug("GET /issues/search?jql={}&start={}&limit={}&search={}&status={}&priority={}&sortField={}&sortDir={}&facets={}",
                jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, sortField, sortDir, includeFacets);

        if (jqlQuery == null || jqlQuery.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("JQL query parameter is required"))
                    .build();
        }

        try {
            IssueSearchResponseDTO response = issueService.searchIssues(
                    jqlQuery,
                    startIndex,
                    pageSize,
                    searchTerm,
                    statusFilter,
                    priorityFilter,
                    sortField,
                    sortDir,
                    includeFacets
            );
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

    /**
     * Convert IssueSearchResponseDTO to Map to bypass JAX-RS Jackson serialization issues.
     */
    private Map<String, Object> convertIssueSearchResponseToMap(IssueSearchResponseDTO dto) {
        Map<String, Object> map = new HashMap<>();

        List<Map<String, Object>> issueMaps = dto.getIssues() != null
                ? dto.getIssues().stream()
                .map(this::convertIssueDtoToMap)
                .collect(Collectors.toList())
                : Collections.emptyList();

        map.put("issues", issueMaps);
        map.put("totalCount", dto.getTotalCount());
        map.put("startIndex", dto.getStartIndex());
        map.put("pageSize", dto.getPageSize());
        map.put("hasNextPage", dto.isHasNextPage());
        map.put("hasPreviousPage", dto.isHasPreviousPage());
        map.put("jqlQuery", dto.getJqlQuery());
        map.put("projectKey", dto.getProjectKey());
        map.put("executionTimeMs", dto.getExecutionTimeMs());
        map.put("currentPage", dto.getCurrentPage());
        map.put("totalPages", dto.getTotalPages());

        // User context for debugging permission issues
        map.put("searchedAsUserKey", dto.getSearchedAsUserKey());
        map.put("searchedAsUserName", dto.getSearchedAsUserName());
        map.put("searchedAsUserDisplayName", dto.getSearchedAsUserDisplayName());
        map.put("resolvedJqlQuery", dto.getResolvedJqlQuery());

        // NEW: facets for filter dropdowns
        map.put("facets", dto.getFacets());

        return map;
    }

    /**
     * Convert IssueDTO to Map for safe JSON serialization.
     */
    private Map<String, Object> convertIssueDtoToMap(IssueDTO issue) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", issue.getKey());
        map.put("id", issue.getId());
        map.put("summary", issue.getSummary());
        map.put("description", issue.getDescription());
        map.put("status", issue.getStatus());
        map.put("statusId", issue.getStatusId());
        map.put("statusIconUrl", issue.getStatusIconUrl());
        map.put("statusCategoryKey", issue.getStatusCategoryKey());
        map.put("priority", issue.getPriority());
        map.put("priorityId", issue.getPriorityId());
        map.put("priorityIconUrl", issue.getPriorityIconUrl());
        map.put("issueType", issue.getIssueType());
        map.put("issueTypeId", issue.getIssueTypeId());
        map.put("issueTypeIconUrl", issue.getIssueTypeIconUrl());
        map.put("projectKey", issue.getProjectKey());
        map.put("projectName", issue.getProjectName());
        map.put("serviceDeskId", issue.getServiceDeskId());
        map.put("created", issue.getCreated());
        map.put("updated", issue.getUpdated());
        map.put("dueDate", issue.getDueDate());
        map.put("reporter", issue.getReporter());
        map.put("reporterDisplayName", issue.getReporterDisplayName());
        map.put("reporterEmailAddress", issue.getReporterEmailAddress());
        map.put("reporterAvatarUrl", issue.getReporterAvatarUrl());
        map.put("assignee", issue.getAssignee());
        map.put("assigneeDisplayName", issue.getAssigneeDisplayName());
        map.put("assigneeEmailAddress", issue.getAssigneeEmailAddress());
        map.put("assigneeAvatarUrl", issue.getAssigneeAvatarUrl());
        map.put("resolution", issue.getResolution());
        map.put("resolutionId", issue.getResolutionId());
        map.put("resolutionDate", issue.getResolutionDate());
        map.put("labels", issue.getLabels());
        map.put("components", issue.getComponents());
        map.put("fixVersions", issue.getFixVersions());
        map.put("affectedVersions", issue.getAffectedVersions());
        map.put("environment", issue.getEnvironment());
        map.put("timeOriginalEstimate", issue.getTimeOriginalEstimate());
        map.put("timeEstimate", issue.getTimeEstimate());
        map.put("timeSpent", issue.getTimeSpent());
        map.put("votes", issue.getVotes());
        map.put("watcherCount", issue.getWatcherCount());
        map.put("customFields", issue.getCustomFields());
        return map;
    }
}
