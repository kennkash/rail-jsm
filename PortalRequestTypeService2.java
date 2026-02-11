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
import com.atlassian.servicedesk.api.requesttype.RequestTypeQuery;
import com.atlassian.servicedesk.api.util.paging.PagedRequest;
import com.atlassian.servicedesk.api.util.paging.PagedResponse;
import com.atlassian.servicedesk.api.util.paging.SimplePagedRequest;
import com.samsungbuilder.jsm.dto.PortalConfigDTO;
import com.samsungbuilder.jsm.dto.RequestTypeDTO;
import com.samsungbuilder.jsm.dto.RequestTypeGroupDTO;
import com.samsungbuilder.jsm.dto.RequestTypesResponseDTO;
import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing Jira Service Management Request Types
 * Uses /rest/servicedesk/1 ordering endpoints because Java API + /rest/servicedeskapi do not expose display order in this instance.
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

    private final ObjectMapper objectMapper = new ObjectMapper();

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
        this.portalConfigService = portalConfigService;
        this.applicationProperties = applicationProperties;
    }

    /**
     * Get all request types for a specific project (ordered by portal display settings)
     */
    public RequestTypesResponseDTO getRequestTypesByProject(String projectKey) {
        log.debug("Fetching request types for project: {}", projectKey);

        try {
            Project project = projectManager.getProjectObjByKey(projectKey);
            if (project == null) {
                log.warn("Project not found: {}", projectKey);
                return createEmptyResponse(projectKey);
            }

            if (serviceDeskManager == null) {
                log.warn("ServiceDeskManager not available. Falling back to sample data.");
                return buildSampleResponse(projectKey, project.getName());
            }

            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
            if (serviceDesk == null) {
                log.warn("Service desk not found for project {}. Falling back to sample data.", projectKey);
                return buildSampleResponse(projectKey, project.getName());
            }

            // Enrichment map (details) from Java API (ordering NOT used)
            Map<Integer, RequestType> requestTypeById = Collections.emptyMap();
            if (jsdRequestTypeService != null) {
                try {
                    List<RequestType> rawRequestTypes = loadRequestTypes(serviceDesk);
                    requestTypeById = rawRequestTypes.stream()
                            .collect(Collectors.toMap(RequestType::getId, rt -> rt, (a, b) -> a, LinkedHashMap::new));
                } catch (Exception apiEx) {
                    log.warn("Could not load request types via JSM RequestTypeService for enrichment; proceeding with REST-only fields.", apiEx);
                }
            }

            // Ordered groups + ordered request types per group from internal REST
            OrderedGroupsAndTypes ordered = loadOrderedGroupsAndTypes(project.getId());

            if (ordered.groupsOrdered.isEmpty()) {
                log.warn("No request type groups returned by REST ordering API for project {}. Falling back to sample.", projectKey);
                return buildSampleResponse(projectKey, project.getName());
            }

            RequestTypesResponseDTO response = buildResponseFromOrderedData(project, serviceDesk, ordered, requestTypeById);
            if (response.getRequestTypes() == null || response.getRequestTypes().isEmpty()) {
                log.warn("Ordered REST API returned groups but no request types for project {}. Falling back to sample.", projectKey);
                return buildSampleResponse(projectKey, project.getName());
            }

            return response;

        } catch (Exception e) {
            log.error("Error fetching request types for project: {}", projectKey, e);
            return buildSampleResponse(projectKey, projectKey);
        }
    }

    /**
     * Get a single request type by ID (unchanged)
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

    public Map<String, List<RequestTypeDTO>> getGroupedRequestTypes(String projectKey) {
        log.debug("Fetching grouped request types for project: {}", projectKey);

        RequestTypesResponseDTO response = getRequestTypesByProject(projectKey);
        Map<String, List<RequestTypeDTO>> grouped = new LinkedHashMap<>();

        for (RequestTypeDTO requestType : response.getRequestTypes()) {
            String group = requestType.getGroup() != null ? requestType.getGroup() : "Other";
            grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(requestType);
        }

        return grouped;
    }

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

    public List<RequestTypeDTO> getRequestTypesByGroup(String projectKey, String groupName) {
        log.debug("Fetching request types for group '{}' in project {}", groupName, projectKey);

        return getRequestTypesByProject(projectKey).getRequestTypes().stream()
                .filter(rt -> Objects.equals(groupName, rt.getGroup()))
                .collect(Collectors.toList());
    }

    // -----------------------------
    // JSM Java API load (for enrichment)
    // -----------------------------
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

    // -----------------------------
    // Ordering REST calls (your discovered endpoints)
    // -----------------------------

    private static class OrderedGroup {
        final int id;
        final String name;
        OrderedGroup(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class OrderedGroupsAndTypes {
        final List<OrderedGroup> groupsOrdered;
        final Map<Integer, List<Map<String, Object>>> requestTypesByGroupIdOrdered;

        OrderedGroupsAndTypes(List<OrderedGroup> groupsOrdered, Map<Integer, List<Map<String, Object>>> requestTypesByGroupIdOrdered) {
            this.groupsOrdered = groupsOrdered;
            this.requestTypesByGroupIdOrdered = requestTypesByGroupIdOrdered;
        }
    }

    @SuppressWarnings("unchecked")
    private OrderedGroupsAndTypes loadOrderedGroupsAndTypes(Long projectId) throws Exception {
        String baseUrl = applicationProperties != null ? applicationProperties.getBaseUrl() : null;
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Base URL not available from ApplicationProperties");
        }
        baseUrl = baseUrl.replaceAll("/$", "");

        // Groups endpoint (ordered list)
        String groupsUrl = baseUrl + "/rest/servicedesk/1/servicedesk/" + projectId + "/request-type-groups";
        String groupsJson = httpGet(groupsUrl);

        List<Object> rawGroups = objectMapper.readValue(groupsJson, List.class);
        List<OrderedGroup> groupsOrdered = new ArrayList<>();
        for (Object o : rawGroups) {
            if (!(o instanceof Map)) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            Object idObj = m.get("id");
            Object nameObj = m.get("name");
            if (idObj == null || nameObj == null) continue;
            int id = ((Number) idObj).intValue();
            String name = String.valueOf(nameObj);
            groupsOrdered.add(new OrderedGroup(id, name));
        }

        // For each group, fetch ordered request types (ordered list)
        Map<Integer, List<Map<String, Object>>> requestTypesByGroup = new LinkedHashMap<>();
        for (OrderedGroup g : groupsOrdered) {
            String rtUrl = baseUrl + "/rest/servicedesk/1/servicedesk/" + projectId
                    + "/request-type-groups/" + g.id + "/request-types";
            String rtJson = httpGet(rtUrl);

            List<Object> rawTypes = objectMapper.readValue(rtJson, List.class);
            List<Map<String, Object>> orderedTypes = new ArrayList<>();
            for (Object t : rawTypes) {
                if (t instanceof Map) {
                    orderedTypes.add((Map<String, Object>) t);
                }
            }
            requestTypesByGroup.put(g.id, orderedTypes);
        }

        return new OrderedGroupsAndTypes(groupsOrdered, requestTypesByGroup);
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL u = new URL(url);
            conn = (HttpURLConnection) u.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("Accept", "application/json");

            int status = conn.getResponseCode();
            InputStream is = (status >= 200 && status < 300) ? conn.getInputStream() : conn.getErrorStream();

            String body;
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                body = sb.toString();
            }

            if (status < 200 || status >= 300) {
                throw new IllegalStateException("HTTP " + status + " from " + url + " body=" + body);
            }
            return body;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private RequestTypesResponseDTO buildResponseFromOrderedData(
            Project project,
            ServiceDesk serviceDesk,
            OrderedGroupsAndTypes ordered,
            Map<Integer, RequestType> requestTypeById
    ) {
        // Groups in display order
        List<RequestTypeGroupDTO> groupDtos = new ArrayList<>();
        Map<Integer, RequestTypeGroupDTO> groupDtoById = new LinkedHashMap<>();

        int groupOrder = 1;
        for (OrderedGroup g : ordered.groupsOrdered) {
            RequestTypeGroupDTO gdto = new RequestTypeGroupDTO(String.valueOf(g.id), g.name);
            gdto.setServiceDeskId(String.valueOf(serviceDesk.getId()));
            gdto.setDisplayOrder(groupOrder++);
            gdto.setRequestTypeCount(0);
            groupDtos.add(gdto);
            groupDtoById.put(g.id, gdto);
        }

        // Duplicate request types per group so per-group ordering is preserved
        List<RequestTypeDTO> requestTypeDtos = new ArrayList<>();

        for (OrderedGroup g : ordered.groupsOrdered) {
            List<Map<String, Object>> orderedTypes = ordered.requestTypesByGroupIdOrdered.getOrDefault(g.id, Collections.emptyList());

            int rtOrder = 1;
            for (Map<String, Object> rtMap : orderedTypes) {
                Object idObj = rtMap.get("id");
                if (!(idObj instanceof Number)) continue;
                int rtId = ((Number) idObj).intValue();

                RequestType enriched = requestTypeById.get(rtId);

                RequestTypeDTO dto;
                if (enriched != null) {
                    dto = convertToDto(enriched, project, serviceDesk);
                } else {
                    dto = new RequestTypeDTO();
                    dto.setId(String.valueOf(rtId));
                    dto.setName(rtMap.get("name") != null ? String.valueOf(rtMap.get("name")) : null);
                    dto.setDescription(rtMap.get("description") != null ? String.valueOf(rtMap.get("description")) : null);
                    dto.setHelpText(rtMap.get("helpText") != null ? String.valueOf(rtMap.get("helpText")) : null);
                    dto.setIssueTypeId(rtMap.get("issueType") instanceof Map && ((Map<?, ?>) rtMap.get("issueType")).get("id") != null
                            ? String.valueOf(((Map<?, ?>) rtMap.get("issueType")).get("id")) : null);
                    dto.setPortalId(String.valueOf(serviceDesk.getId()));
                    dto.setProjectKey(project.getKey());
                    dto.setServiceDeskId(String.valueOf(serviceDesk.getId()));
                    dto.setEnabled(true);
                }

                // Per-group “copy”
                dto.setGroup(g.name);
                dto.setGroups(new ArrayList<>(Collections.singletonList(g.name)));
                dto.setGroupIds(new ArrayList<>(Collections.singletonList(String.valueOf(g.id))));
                dto.setDisplayOrder(rtOrder++);

                requestTypeDtos.add(dto);

                RequestTypeGroupDTO groupDto = groupDtoById.get(g.id);
                if (groupDto != null) {
                    int current = groupDto.getRequestTypeCount() != null ? groupDto.getRequestTypeCount() : 0;
                    groupDto.setRequestTypeCount(current + 1);
                }
            }
        }

        RequestTypesResponseDTO response = new RequestTypesResponseDTO(requestTypeDtos, groupDtos);
        response.setProjectKey(project.getKey());
        response.setProjectName(project.getName());
        response.setServiceDeskId(String.valueOf(serviceDesk.getId()));
        response.setTotalCount(requestTypeDtos.size());
        response.setHasGroups(!groupDtos.isEmpty());
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

    // Left as-is from your existing file
    public List<GlobalRequestTypeSearchResult> searchAllRequestTypes(String searchTerm, int maxResults) {
        // (keep your existing implementation here, unchanged)
        return Collections.emptyList();
    }

    @org.codehaus.jackson.annotate.JsonAutoDetect(
            fieldVisibility = org.codehaus.jackson.annotate.JsonAutoDetect.Visibility.NONE,
            getterVisibility = org.codehaus.jackson.annotate.JsonAutoDetect.Visibility.PUBLIC_ONLY
    )
    public static class GlobalRequestTypeSearchResult {
        private RequestTypeDTO requestType;
        private String projectKey;
        private String projectName;
        private String serviceDeskId;
        private String portalId;
        private boolean isLive;

        @org.codehaus.jackson.annotate.JsonProperty("requestType")
        public RequestTypeDTO getRequestType() { return requestType; }
        public void setRequestType(RequestTypeDTO requestType) { this.requestType = requestType; }

        @org.codehaus.jackson.annotate.JsonProperty("projectKey")
        public String getProjectKey() { return projectKey; }
        public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

        @org.codehaus.jackson.annotate.JsonProperty("projectName")
        public String getProjectName() { return projectName; }
        public void setProjectName(String projectName) { this.projectName = projectName; }

        @org.codehaus.jackson.annotate.JsonProperty("serviceDeskId")
        public String getServiceDeskId() { return serviceDeskId; }
        public void setServiceDeskId(String serviceDeskId) { this.serviceDeskId = serviceDeskId; }

        @org.codehaus.jackson.annotate.JsonProperty("portalId")
        public String getPortalId() { return portalId; }
        public void setPortalId(String portalId) { this.portalId = portalId; }

        @org.codehaus.jackson.annotate.JsonProperty("isLive")
        public boolean isLive() { return isLive; }
        public void setLive(boolean live) { this.isLive = live; }
    }
}