/* /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/service/PortalRequestTypeService.java */

package com.samsungbuilder.jsm.service;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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

    @Inject
    public PortalRequestTypeService(
            @ComponentImport ProjectManager projectManager,
            @ComponentImport ServiceDeskManager serviceDeskManager,
            @ComponentImport com.atlassian.servicedesk.api.requesttype.RequestTypeService requestTypeService,
            @ComponentImport JiraAuthenticationContext authenticationContext,
            PortalConfigService portalConfigService
    ) {
        this.projectManager = projectManager;
        this.serviceDeskManager = serviceDeskManager;
        this.jsdRequestTypeService = requestTypeService;
        this.authenticationContext = authenticationContext;
        this.portalConfigService = portalConfigService;
    }

    /**
     * Get all request types for a specific project
     *
     * @param projectKey The project key (e.g., "DEMO", "SUP")
     * @return Response DTO containing request types and groups
     */
    public RequestTypesResponseDTO getRequestTypesByProject(String projectKey) {
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

            List<RequestType> rawRequestTypes = loadRequestTypes(serviceDesk);
            if (rawRequestTypes.isEmpty()) {
                log.warn("No request types returned by API for project {}. Falling back to sample data.", projectKey);
                return buildSampleResponse(projectKey, project.getName());
            }

            return buildResponseFromRequestTypes(project, serviceDesk, rawRequestTypes);

        } catch (Exception e) {
            log.error("Error fetching request types for project: {}", projectKey, e);
            return buildSampleResponse(projectKey, projectKey);
        }
    }

    /**
     * Get a single request type by ID
     *
     * @param requestTypeId The request type ID
     * @return Request type DTO or null if not found
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
     * Get request types grouped by category
     *
     * @param projectKey The project key
     * @return Map of group name to request types
     */
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

    /**
     * Search request types by name or description
     *
     * @param projectKey The project key
     * @param searchTerm The search term
     * @return List of matching request types
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
     * Filter request types by group
     *
     * @param projectKey The project key
     * @param groupName  The group name to filter by
     * @return List of request types in the group
     */
    public List<RequestTypeDTO> getRequestTypesByGroup(String projectKey, String groupName) {
        log.debug("Fetching request types for group '{}' in project {}", groupName, projectKey);

        return getRequestTypesByProject(projectKey).getRequestTypes().stream()
                .filter(rt -> Objects.equals(groupName, rt.getGroup()))
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

        // Extract display order - RequestType doesn't have getOrder(), so we'll use the position in the list
        // The actual sorting will be handled by the frontend based on the order returned from the API
        // For now, we'll leave displayOrder as null and let the frontend handle the default sorting
        // TODO: Investigate JSM API for proper display order field

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

    /**
     * Sample fallback data (used for development or when JSM APIs unavailable)
     */
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

    /**
     * Search request types across all service desk projects
     *
     * @param searchTerm The search term
     * @param maxResults Maximum number of results to return
     * @return List of matching request types with project context
     */
    public List<GlobalRequestTypeSearchResult> searchAllRequestTypes(String searchTerm, int maxResults) {
        log.debug("Searching all request types for '{}'", searchTerm);

        String normalizedSearch = searchTerm != null ? searchTerm.trim().toLowerCase(Locale.ENGLISH) : "";
        if (normalizedSearch.isEmpty()) {
            return Collections.emptyList();
        }

        List<GlobalRequestTypeSearchResult> results = new ArrayList<>();

        if (serviceDeskManager == null || jsdRequestTypeService == null || projectManager == null) {
            log.warn("Service Desk APIs not available for global search");
            return results;
        }

        try {
            // Get all projects and check which ones have service desks
            List<Project> allProjects = projectManager.getProjectObjects();

            for (Project project : allProjects) {
                if (results.size() >= maxResults) {
                    break;
                }

                try {
                    // Check if this project has a service desk
                    ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
                    if (serviceDesk == null) {
                        continue;
                    }

                    String projectKey = project.getKey();
                    String projectName = project.getName();

                    // Get portal config to determine if portal is Live
                    boolean isLive = false;
                    if (portalConfigService != null) {
                        Optional<PortalConfigDTO> configOpt = portalConfigService.getPortalConfig(projectKey);
                        isLive = configOpt.map(PortalConfigDTO::isLive).orElse(false);
                    }

                    // Get the JSM portal ID for OOTB navigation
                    String portalId = String.valueOf(serviceDesk.getId());

                    // Load request types for this service desk
                    List<RequestType> requestTypes = loadRequestTypes(serviceDesk);

                    for (RequestType rt : requestTypes) {
                        if (results.size() >= maxResults) {
                            break;
                        }

                        String name = rt.getName() != null ? rt.getName().toLowerCase(Locale.ENGLISH) : "";
                        String description = rt.getDescription() != null ? rt.getDescription().toLowerCase(Locale.ENGLISH) : "";

                        if (name.contains(normalizedSearch) || description.contains(normalizedSearch)) {
                            RequestTypeDTO dto = convertToDto(rt, project, serviceDesk);
                            GlobalRequestTypeSearchResult result = new GlobalRequestTypeSearchResult();
                            result.setRequestType(dto);
                            result.setProjectKey(projectKey);
                            result.setProjectName(projectName);
                            result.setServiceDeskId(portalId);
                            result.setPortalId(portalId);
                            result.setLive(isLive);
                            results.add(result);
                        }
                    }
                } catch (Exception e) {
                    log.warn("Error searching request types for project {}: {}", project.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error performing global request type search", e);
        }

        log.debug("Global search for '{}' returned {} results", searchTerm, results.size());
        return results;
    }

    /**
     * DTO for global request type search results.
     * Uses Jackson 1.x annotations for Jira compatibility.
     */
    @org.codehaus.jackson.annotate.JsonAutoDetect(
        fieldVisibility = org.codehaus.jackson.annotate.JsonAutoDetect.Visibility.NONE,
        getterVisibility = org.codehaus.jackson.annotate.JsonAutoDetect.Visibility.PUBLIC_ONLY
    )
    public static class GlobalRequestTypeSearchResult {
        private RequestTypeDTO requestType;
        private String projectKey;
        private String projectName;
        private String serviceDeskId;
        private String portalId;  // JSM portal ID for OOTB navigation
        private boolean isLive;   // Whether this portal uses RAIL custom portal

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
