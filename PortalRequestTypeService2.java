/* /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/service/PortalRequestTypeService.java */
package com.samsungbuilder.jsm.service;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.ServiceDeskManager;
import com.atlassian.servicedesk.api.requesttype.RequestType;
import com.atlassian.servicedesk.api.requesttype.RequestTypeGroup;
import com.atlassian.servicedesk.api.requesttype.RequestTypeQuery;
import com.atlassian.servicedesk.api.util.paging.PagedRequest;
import com.atlassian.servicedesk.api.util.paging.PagedResponse;
import com.atlassian.servicedesk.api.util.paging.SimplePagedRequest;
import com.samsungbuilder.jsm.dto.PortalConfigDTO;
import com.samsungbuilder.jsm.dto.RequestTypeDTO;
import com.samsungbuilder.jsm.dto.RequestTypeGroupDTO;
import com.samsungbuilder.jsm.dto.RequestTypesResponseDTO;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Jira Service Management Request Types
 * Provides methods to fetch, filter, and organize request types for portal display
 */
@Named
public class PortalRequestTypeService {

    private static final Logger log = LoggerFactory.getLogger(PortalRequestTypeService.class);
    private static final int PAGE_SIZE = 50;

    private final ProjectManager projectManager;
    private final ServiceDeskManager serviceDeskManager;
    private final com.atlassian.servicedesk.api.requesttype.RequestTypeService jsdRequestTypeService;
    private final JiraAuthenticationContext authenticationContext;
    private final PortalConfigService portalConfigService;
    private final ApplicationProperties applicationProperties;

    @Inject
    public PortalRequestTypeService(
            @ComponentImport ProjectManager projectManager,
            @ComponentImport ServiceDeskManager serviceDeskManager,
            @ComponentImport com.atlassian.servicedesk.api.requesttype.RequestTypeService requestTypeService,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            @ComponentImport ApplicationProperties applicationProperties,
            PortalConfigService portalConfigService
    ) {
        this.projectManager = projectManager;
        this.serviceDeskManager = serviceDeskManager;
        this.jsdRequestTypeService = requestTypeService;
        this.authenticationContext = authenticationContext;
        this.applicationProperties = applicationProperties;
        this.portalConfigService = portalConfigService;
    }

    /**
     * Existing signature (kept for compatibility). If you need ordering-per-group, prefer the overload
     * that accepts cookieHeader.
     */
    public RequestTypesResponseDTO getRequestTypesByProject(String projectKey) {
        return getRequestTypesByProject(projectKey, null);
    }

    /**
     * Get all request types for a specific project, grouped and ordered exactly like JSM portal settings.
     *
     * Ordering source of truth:
     *  - /rest/servicedesk/1/servicedesk/{projectId}/request-type-groups  (group order = array order)
     *  - /rest/servicedesk/1/servicedesk/{projectId}/request-type-groups/{groupId}/request-types (request type order within group = array order)
     *
     * @param projectKey    The project key
     * @param cookieHeader  Optional Cookie header from the incoming request (recommended)
     */
    public RequestTypesResponseDTO getRequestTypesByProject(String projectKey, String cookieHeader) {
        log.debug("Fetching request types for project: {}", projectKey);

        try {
            Project project = projectManager.getProjectObjByKey(projectKey);
            if (project == null) {
                log.warn("Project not found: {}", projectKey);
                return createEmptyResponse(projectKey);
            }

            if (jsdRequestTypeService == null || serviceDeskManager == null) {
                log.warn("Jira Service Management APIs not available. Falling back to sample data.");
                return buildSampleResponse(projectKey, project.getName());
            }

            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
            if (serviceDesk == null) {
                log.warn("Service desk not found for project {}. Falling back to sample data.", projectKey);
                return buildSampleResponse(projectKey, project.getName());
            }

            // We still load request types via Java API as a fallback/data source
            List<RequestType> rawRequestTypes = loadRequestTypes(serviceDesk);

            // ✅ Build groups with ordered request types using legacy endpoints
            RequestTypesResponseDTO ordered = buildGroupedOrderedResponse(project, serviceDesk, cookieHeader);

            // If legacy endpoints failed, fall back to old behavior
            if (ordered == null || ordered.getGroups() == null || ordered.getGroups().isEmpty()) {
                if (rawRequestTypes.isEmpty()) {
                    log.warn("No request types returned by API for project {}. Falling back to sample data.", projectKey);
                    return buildSampleResponse(projectKey, project.getName());
                }
                return buildResponseFromRequestTypes(project, serviceDesk, rawRequestTypes);
            }

            // Also set top-level properties
            ordered.setProjectKey(project.getKey());
            ordered.setProjectName(project.getName());
            ordered.setServiceDeskId(String.valueOf(serviceDesk.getId()));
            ordered.setHasGroups(true);

            return ordered;

        } catch (Exception e) {
            log.error("Error fetching request types for project: {}", projectKey, e);
            return buildSampleResponse(projectKey, projectKey);
        }
    }

    /**
     * Get a single request type by ID
     */
    public RequestTypeDTO getRequestTypeById(String requestTypeId) {
        log.debug("Fetching request type by ID: {}", requestTypeId);

        if (jsdRequestTypeService == null) {
            log.warn("Jira Service Management APIs not available. Falling back to sample data lookup.");
            return getSampleRequestTypes("sample").stream()
                    .filter(rt -> Objects.equals(rt.getId(), requestTypeId))
                    .findFirst()
                    .orElse(null);
        }

        try {
            Integer numericId = Integer.valueOf(requestTypeId);
            ApplicationUser user = authenticationContext != null ? authenticationContext.getLoggedInUser() : null;

            RequestTypeQuery query = jsdRequestTypeService.newQueryBuilder()
                    .requestType(numericId)
                    .requestOverrideSecurity(Boolean.TRUE)
                    .build();

            PagedResponse<RequestType> response = jsdRequestTypeService.getRequestTypes(user, query);

            return response.findFirst()
                    .map(rt -> convertToDto(rt, null, null))
                    .orElse(null);

        } catch (NumberFormatException ex) {
            log.warn("Request type id {} is not numeric", requestTypeId);
            return null;
        } catch (Exception e) {
            log.error("Error fetching request type by id {}", requestTypeId, e);
            return null;
        }
    }

    /**
     * Search request types by name or description (uses grouped response and flattens)
     */
    public List<RequestTypeDTO> searchRequestTypes(String projectKey, String searchTerm) {
        log.debug("Searching request types for '{}' in project {}", searchTerm, projectKey);

        String normalizedSearch = searchTerm != null ? searchTerm.trim().toLowerCase(Locale.ENGLISH) : "";
        if (normalizedSearch.isEmpty()) {
            return getRequestTypesByProject(projectKey).getRequestTypes();
        }

        return getRequestTypesByProject(projectKey).getRequestTypes().stream()
                .filter(rt ->
                        (rt.getName() != null && rt.getName().toLowerCase(Locale.ENGLISH).contains(normalizedSearch)) ||
                                (rt.getDescription() != null && rt.getDescription().toLowerCase(Locale.ENGLISH).contains(normalizedSearch)))
                .collect(Collectors.toList());
    }

    /**
     * Filter request types by group name (uses grouped response)
     */
    public List<RequestTypeDTO> getRequestTypesByGroup(String projectKey, String groupName) {
        log.debug("Fetching request types for group '{}' in project {}", groupName, projectKey);

        RequestTypesResponseDTO response = getRequestTypesByProject(projectKey);
        if (response.getGroups() == null) return Collections.emptyList();

        return response.getGroups().stream()
                .filter(g -> Objects.equals(groupName, g.getName()))
                .flatMap(g -> (g.getRequestTypes() != null ? g.getRequestTypes().stream() : java.util.stream.Stream.empty()))
                .collect(Collectors.toList());
    }

    private List<RequestType> loadRequestTypes(ServiceDesk serviceDesk) {
        List<RequestType> requestTypes = new ArrayList<>();
        ApplicationUser user = authenticationContext != null ? authenticationContext.getLoggedInUser() : null;

        int start = 0;
        boolean hasMore = true;

        while (hasMore) {
            RequestTypeQuery query = jsdRequestTypeService.newQueryBuilder()
                    .serviceDesk(serviceDesk.getId())
                    .pagedRequest(SimplePagedRequest.paged(start, PAGE_SIZE))
                    .requestOverrideSecurity(Boolean.TRUE)
                    .filterHidden(Boolean.TRUE)
                    .build();

            PagedResponse<RequestType> response = jsdRequestTypeService.getRequestTypes(user, query);
            requestTypes.addAll(response.getResults());

            if (response.hasNextPage()) {
                PagedRequest pagedRequest = response.getPagedRequest();
                start = pagedRequest.getStart() + pagedRequest.getLimit();
            } else {
                hasMore = false;
            }
        }

        return requestTypes;
    }

    /**
     * ✅ Builds groups + ordered request types using the legacy endpoints you found.
     * Returns RequestTypesResponseDTO where groups[i].requestTypes is already in correct order.
     */
    private RequestTypesResponseDTO buildGroupedOrderedResponse(Project project, ServiceDesk serviceDesk, String cookieHeader) {
        try {
            long serviceDeskId = serviceDesk.getId();
            String baseUrl = applicationProperties.getBaseUrl();

            // 1) Load groups (ordered)
            String groupsUrl = baseUrl + "/rest/servicedesk/1/servicedesk/" + serviceDeskId + "/request-type-groups";
            List<Map<String, Object>> groupList = httpGetJsonArray(groupsUrl, cookieHeader);

            if (groupList == null || groupList.isEmpty()) {
                log.warn("Legacy groups endpoint returned empty for serviceDeskId {}", serviceDeskId);
                return null;
            }

            // 2) Build group DTOs in returned order
            List<RequestTypeGroupDTO> groupDtos = new ArrayList<>();
            int groupDisplayOrder = 1;

            // Optional: build a deduped top-level requestTypes list by id (useful for search)
            Map<String, RequestTypeDTO> uniqueById = new LinkedHashMap<>();

            for (Map<String, Object> g : groupList) {
                Integer groupId = asInt(g.get("id"));
                String groupName = asString(g.get("name"));

                if (groupId == null || groupName == null) continue;

                RequestTypeGroupDTO groupDto = new RequestTypeGroupDTO(String.valueOf(groupId), groupName);
                groupDto.setServiceDeskId(String.valueOf(serviceDeskId));
                groupDto.setDisplayOrder(groupDisplayOrder++);

                // 3) Load request types for this group (ordered)
                String rtUrl = baseUrl + "/rest/servicedesk/1/servicedesk/" + serviceDeskId
                        + "/request-type-groups/" + groupId + "/request-types";

                List<Map<String, Object>> rtList = httpGetJsonArray(rtUrl, cookieHeader);
                if (rtList == null) rtList = Collections.emptyList();

                int rtDisplayOrder = 1;
                for (Map<String, Object> rt : rtList) {
                    Integer rtId = asInt(rt.get("id"));
                    String rtName = asString(rt.get("name"));
                    String rtDesc = asString(rt.get("description"));
                    String helpText = asString(rt.get("helpText"));

                    if (rtId == null || rtName == null) continue;

                    RequestTypeDTO dto = new RequestTypeDTO();
                    dto.setId(String.valueOf(rtId));
                    dto.setName(rtName);
                    dto.setDescription(rtDesc);
                    dto.setHelpText(helpText);
                    dto.setProjectKey(project.getKey());
                    dto.setServiceDeskId(String.valueOf(serviceDeskId));
                    dto.setPortalId(String.valueOf(serviceDeskId));
                    dto.setEnabled(true);

                    // ✅ IMPORTANT: order is based on JSON array position (not the "order" field)
                    dto.setDisplayOrder(rtDisplayOrder++);

                    // For THIS group rendering context
                    dto.setGroup(groupName);

                    // groups + groupIds from payload (so multi-group membership is preserved)
                    Object groupsObj = rt.get("groups");
                    if (groupsObj instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> groupsPayload = (List<Object>) groupsObj;
                        for (Object gi : groupsPayload) {
                            if (gi instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> gm = (Map<String, Object>) gi;
                                Integer gid = asInt(gm.get("id"));
                                String gname = asString(gm.get("name"));
                                if (gid != null) dto.getGroupIds().add(String.valueOf(gid));
                                if (gname != null) dto.getGroups().add(gname);
                            }
                        }
                    }

                    // icon id exists in payload, keep it for debugging / future use
                    Integer iconId = asInt(rt.get("icon"));
                    if (iconId != null) dto.setIcon(String.valueOf(iconId));

                    groupDto.addRequestType(dto);

                    // Add to unique map (dedupe by id) for response.requestTypes
                    uniqueById.putIfAbsent(dto.getId(), dto);
                }

                groupDtos.add(groupDto);
            }

            RequestTypesResponseDTO response = new RequestTypesResponseDTO(new ArrayList<>(uniqueById.values()), groupDtos);
            response.setProjectKey(project.getKey());
            response.setProjectName(project.getName());
            response.setServiceDeskId(String.valueOf(serviceDeskDeskIdOr(serviceDesk)));
            response.setHasGroups(true);
            response.setTotalCount(uniqueById.size());
            return response;

        } catch (Exception e) {
            log.warn("Failed to build grouped ordered response: {}", e.getMessage());
            return null;
        }
    }

    private long serviceDeskDeskIdOr(ServiceDesk sd) {
        try { return sd.getId(); } catch (Exception e) { return 0L; }
    }

    /**
     * Fallback: old behavior (kept). This returns a flat list only.
     */
    private RequestTypesResponseDTO buildResponseFromRequestTypes(Project project, ServiceDesk serviceDesk, List<RequestType> requestTypes) {
        List<RequestTypeDTO> dtoList = new ArrayList<>();
        Map<Integer, RequestTypeGroupDTO> groupMap = new LinkedHashMap<>();

        for (RequestType requestType : requestTypes) {
            RequestTypeDTO dto = convertToDto(requestType, project, serviceDesk);

            if (requestType.getGroups() != null && !requestType.getGroups().isEmpty()) {
                for (RequestTypeGroup group : requestType.getGroups()) {
                    RequestTypeGroupDTO groupDTO = groupMap.computeIfAbsent(
                            group.getId(),
                            id -> {
                                RequestTypeGroupDTO dtoGroup = new RequestTypeGroupDTO(String.valueOf(id), group.getName());
                                dtoGroup.setServiceDeskId(String.valueOf(serviceDesk.getId()));
                                group.getOrder().ifPresent(dtoGroup::setDisplayOrder);
                                dtoGroup.setRequestTypeCount(0);
                                return dtoGroup;
                            }
                    );

                    groupDTO.setRequestTypeCount((groupDTO.getRequestTypeCount() == null ? 0 : groupDTO.getRequestTypeCount()) + 1);
                    dto.getGroupIds().add(String.valueOf(group.getId()));
                    dto.getGroups().add(groupDTO.getName());
                    if (dto.getGroup() == null) {
                        dto.setGroup(groupDTO.getName());
                    }
                }
            }

            dtoList.add(dto);
        }

        RequestTypesResponseDTO response = new RequestTypesResponseDTO(dtoList, new ArrayList<>(groupMap.values()));
        response.setProjectKey(project.getKey());
        response.setProjectName(project.getName());
        response.setServiceDeskId(String.valueOf(serviceDesk.getId()));
        response.setTotalCount(dtoList.size());
        response.setHasGroups(!groupMap.isEmpty());
        return response;
    }

    private RequestTypeDTO convertToDto(RequestType requestType, Project project, ServiceDesk serviceDesk) {
        RequestTypeDTO dto = new RequestTypeDTO();
        dto.setId(String.valueOf(requestType.getId()));
        dto.setName(requestType.getName());
        dto.setDescription(requestType.getDescription());
        dto.setHelpText(requestType.getHelpText());
        dto.setIssueTypeId(String.valueOf(requestType.getIssueTypeId()));
        dto.setPortalId(String.valueOf(requestType.getPortalId()));
        dto.setEnabled(true);
        dto.setProjectKey(project != null ? project.getKey() : null);
        dto.setServiceDeskId(serviceDesk != null ? String.valueOf(serviceDesk.getId()) : null);
        return dto;
    }

    private RequestTypesResponseDTO createEmptyResponse(String projectKey) {
        RequestTypesResponseDTO response = new RequestTypesResponseDTO();
        response.setProjectKey(projectKey);
        response.setRequestTypes(Collections.emptyList());
        response.setGroups(Collections.emptyList());
        response.setTotalCount(0);
        response.setHasGroups(false);
        return response;
    }

    private RequestTypesResponseDTO buildSampleResponse(String projectKey, String projectName) {
        List<RequestTypeDTO> requestTypes = getSampleRequestTypes(projectKey);
        List<RequestTypeGroupDTO> groups = getSampleGroups();
        RequestTypesResponseDTO response = new RequestTypesResponseDTO(requestTypes, groups);
        response.setProjectKey(projectKey);
        response.setProjectName(projectName);
        response.setTotalCount(requestTypes.size());
        return response;
    }

    // --------------------------
    // Legacy endpoint HTTP helpers
    // --------------------------

    private List<Map<String, Object>> httpGetJsonArray(String url, String cookieHeader) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "application/json");

            // ✅ Forward cookies from the incoming request so Jira sees the same user/session
            if (cookieHeader != null && !cookieHeader.trim().isEmpty()) {
                conn.setRequestProperty("Cookie", cookieHeader);
            }

            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                log.warn("GET {} returned HTTP {}", url, code);
                return Collections.emptyList();
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(sb.toString(), new TypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("GET {} failed: {}", url, e.getMessage());
            return Collections.emptyList();
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private Integer asInt(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try { return Integer.parseInt(String.valueOf(obj)); } catch (Exception e) { return null; }
    }

    private String asString(Object obj) {
        if (obj == null) return null;
        String s = String.valueOf(obj);
        return s.trim().isEmpty() ? null : s;
    }

    // --------------------------
    // Sample fallback data (unchanged)
    // --------------------------

    private List<RequestTypeDTO> getSampleRequestTypes(String projectKey) {
        List<RequestTypeDTO> requestTypes = new ArrayList<>();

        RequestTypeDTO help = new RequestTypeDTO("help", "Get Help", "Ask a question or get support");
        help.setIcon("HelpCircle");
        help.setColor("blue");
        help.setProjectKey(projectKey);
        help.setDisplayOrder(1);
        assignGroup(help, "support", "Support");
        requestTypes.add(help);

        RequestTypeDTO incident = new RequestTypeDTO("incident", "Report an Incident", "Report a problem or issue");
        incident.setIcon("Bug");
        incident.setColor("red");
        incident.setProjectKey(projectKey);
        incident.setDisplayOrder(2);
        assignGroup(incident, "support", "Support");
        requestTypes.add(incident);

        RequestTypeDTO question = new RequestTypeDTO("question", "Ask a Question", "General questions and inquiries");
        question.setIcon("FileQuestion");
        question.setColor("blue");
        question.setProjectKey(projectKey);
        question.setDisplayOrder(3);
        assignGroup(question, "support", "Support");
        requestTypes.add(question);

        RequestTypeDTO serviceRequest = new RequestTypeDTO("service-request", "Service Request", "Request a service or item");
        serviceRequest.setIcon("Mail");
        serviceRequest.setColor("green");
        serviceRequest.setProjectKey(projectKey);
        serviceRequest.setDisplayOrder(4);
        assignGroup(serviceRequest, "services", "Services");
        requestTypes.add(serviceRequest);

        RequestTypeDTO accessRequest = new RequestTypeDTO("access-request", "Access Request", "Request access to systems or resources");
        accessRequest.setIcon("ShieldCheck");
        accessRequest.setColor("blue");
        accessRequest.setProjectKey(projectKey);
        accessRequest.setDisplayOrder(5);
        assignGroup(accessRequest, "services", "Services");
        requestTypes.add(accessRequest);

        RequestTypeDTO changeRequest = new RequestTypeDTO("change", "Request a Change", "Propose a change or improvement");
        changeRequest.setIcon("Lightbulb");
        changeRequest.setColor("yellow");
        changeRequest.setProjectKey(projectKey);
        changeRequest.setDisplayOrder(6);
        assignGroup(changeRequest, "changes", "Changes");
        requestTypes.add(changeRequest);

        RequestTypeDTO featureRequest = new RequestTypeDTO("feature", "Feature Request", "Suggest a new feature");
        featureRequest.setIcon("Zap");
        featureRequest.setColor("purple");
        featureRequest.setProjectKey(projectKey);
        featureRequest.setDisplayOrder(7);
        assignGroup(featureRequest, "changes", "Changes");
        requestTypes.add(featureRequest);

        return requestTypes;
    }

    private List<RequestTypeGroupDTO> getSampleGroups() {
        List<RequestTypeGroupDTO> groups = new ArrayList<>();
        RequestTypeGroupDTO support = new RequestTypeGroupDTO("support", "Support", "Support related request types");
        support.setDisplayOrder(1);
        support.setRequestTypeCount(3);
        groups.add(support);

        RequestTypeGroupDTO services = new RequestTypeGroupDTO("services", "Services", "Service request types");
        services.setDisplayOrder(2);
        services.setRequestTypeCount(2);
        groups.add(services);

        RequestTypeGroupDTO changes = new RequestTypeGroupDTO("changes", "Changes", "Change and improvement request types");
        changes.setDisplayOrder(3);
        changes.setRequestTypeCount(2);
        groups.add(changes);

        return groups;
    }

    private void assignGroup(RequestTypeDTO dto, String groupId, String groupName) {
        dto.getGroupIds().add(groupId);
        dto.getGroups().add(groupName);
        if (dto.getGroup() == null) {
            dto.setGroup(groupName);
        }
    }
}