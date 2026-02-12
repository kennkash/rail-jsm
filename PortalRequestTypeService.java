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
import java.util.Comparator;
import java.util.HashMap;
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
    private static final int PAGE_SIZE = 100;

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

            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
            if (serviceDesk == null) {
                log.warn("Service desk not found for project {}", projectKey);
                return createEmptyResponse(projectKey);
            }

            // 1. Load ALL request types (Unsorted globally, but contains all data)
            List<RequestType> allRequestTypes = loadAllRequestTypes(serviceDesk);

            if (allRequestTypes.isEmpty()) {
                return createEmptyResponse(projectKey);
            }

            // 2. Build the response and apply Sorting logic
            return buildResponseAndSort(project, serviceDesk, allRequestTypes);

        } catch (Exception e) {
            log.error("Error fetching request types for project: {}", projectKey, e);
            return createEmptyResponse(projectKey);
        }
    }

    /**
     * Get a single request type by ID
     */
    public RequestTypeDTO getRequestTypeById(String requestTypeId) {
        log.debug("Fetching request type by ID: {}", requestTypeId);

        if (jsdRequestTypeService == null) {
            return null;
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
     */
    public Map<String, List<RequestTypeDTO>> getGroupedRequestTypes(String projectKey) {
        log.debug("Fetching grouped request types for project: {}", projectKey);

        RequestTypesResponseDTO response = getRequestTypesByProject(projectKey);
        Map<String, List<RequestTypeDTO>> grouped = new LinkedHashMap<>();

        for (RequestTypeDTO requestType : response.getRequestTypes()) {
            // If it belongs to multiple groups, add it to all of them
            if (requestType.getGroups() != null && !requestType.getGroups().isEmpty()) {
                for (String groupName : requestType.getGroups()) {
                    grouped.computeIfAbsent(groupName, k -> new ArrayList<>()).add(requestType);
                }
            } else {
                String group = requestType.getGroup() != null ? requestType.getGroup() : "Other";
                grouped.computeIfAbsent(group, k -> new ArrayList<>()).add(requestType);
            }
        }

        return grouped;
    }

    /**
     * Search request types by name or description
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
     * Search request types across all service desk projects
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
            List<Project> allProjects = projectManager.getProjectObjects();

            for (Project project : allProjects) {
                if (results.size() >= maxResults) {
                    break;
                }

                try {
                    ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
                    if (serviceDesk == null) {
                        continue;
                    }

                    String projectKey = project.getKey();
                    String projectName = project.getName();

                    boolean isLive = false;
                    if (portalConfigService != null) {
                        Optional<PortalConfigDTO> configOpt = portalConfigService.getPortalConfig(projectKey);
                        isLive = configOpt.map(PortalConfigDTO::isLive).orElse(false);
                    }

                    String portalId = String.valueOf(serviceDesk.getId());
                    List<RequestType> requestTypes = loadAllRequestTypes(serviceDesk);

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

        return results;
    }

    private List<RequestType> loadAllRequestTypes(ServiceDesk serviceDesk) {
        List<RequestType> requestTypes = new ArrayList<>();
        ApplicationUser user = authenticationContext.getLoggedInUser();
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
                start += PAGE_SIZE;
            } else {
                hasMore = false;
            }
        }
        return requestTypes;
    }

    private RequestTypesResponseDTO buildResponseAndSort(Project project, ServiceDesk serviceDesk, List<RequestType> allRequestTypes) {
        // Map DTOs by ID so we can update their order later
        Map<String, RequestTypeDTO> dtoMap = new HashMap<>();
        List<RequestTypeDTO> dtoList = new ArrayList<>();
        
        // Map Groups by ID to ensure deduplication
        Map<Integer, RequestTypeGroupDTO> groupMap = new LinkedHashMap<>();
        
        // Used to track discovery order of groups for the tab order
        List<Integer> groupDiscoveryOrder = new ArrayList<>();

        // 1. Convert everything to DTOs first
        for (RequestType rt : allRequestTypes) {
            RequestTypeDTO dto = convertToDto(rt, project, serviceDesk);
            dtoList.add(dto);
            dtoMap.put(String.valueOf(rt.getId()), dto);

            if (rt.getGroups() != null) {
                for (RequestTypeGroup group : rt.getGroups()) {
                    int groupId = group.getId();

                    // Create Group DTO if we haven't seen this ID before
                    if (!groupMap.containsKey(groupId)) {
                        groupDiscoveryOrder.add(groupId);
                        RequestTypeGroupDTO newGroup = new RequestTypeGroupDTO(String.valueOf(groupId), group.getName());
                        newGroup.setServiceDeskId(String.valueOf(serviceDesk.getId()));
                        
                        // Default group display order is based on discovery order
                        newGroup.setDisplayOrder(groupDiscoveryOrder.size());
                        newGroup.setRequestTypeCount(0);
                        groupMap.put(groupId, newGroup);
                    }

                    RequestTypeGroupDTO groupDTO = groupMap.get(groupId);
                    groupDTO.setRequestTypeCount(groupDTO.getRequestTypeCount() + 1);

                    // Link Group to RT
                    dto.getGroupIds().add(String.valueOf(groupId));
                    dto.getGroups().add(groupDTO.getName());
                    
                    if (dto.getGroup() == null) {
                        dto.setGroup(groupDTO.getName());
                    }
                }
            }
        }

        // 2. Query each group individually to get CORRECT Sort Order
        ApplicationUser user = authenticationContext.getLoggedInUser();

        for (Integer groupId : groupMap.keySet()) {
            try {
                // This query IS sorted by JSM based on drag-and-drop order
                RequestTypeQuery sortedQuery = jsdRequestTypeService.newQueryBuilder()
                        .serviceDesk(serviceDesk.getId())
                        .group(groupId)
                        .pagedRequest(SimplePagedRequest.paged(0, 100))
                        .build();

                List<RequestType> sortedTypes = jsdRequestTypeService.getRequestTypes(user, sortedQuery).getResults();

                // Apply this specific order to our DTOs
                for (int i = 0; i < sortedTypes.size(); i++) {
                    String rtId = String.valueOf(sortedTypes.get(i).getId());
                    RequestTypeDTO dto = dtoMap.get(rtId);

                    if (dto != null) {
                        // Store the index (0, 1, 2...) for this specific group
                        dto.getGroupOrderMap().put(String.valueOf(groupId), i);
                        
                        // Also set main displayOrder if this is the first group (fallback)
                        if (dto.getDisplayOrder() == null) {
                            dto.setDisplayOrder(i);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Could not fetch sorted types for group {}", groupId, e);
            }
        }

        // 3. Sort Groups themselves based on discovery order
        List<RequestTypeGroupDTO> sortedGroups = new ArrayList<>(groupMap.values());
        sortedGroups.sort(Comparator.comparingInt(g -> g.getDisplayOrder() != null ? g.getDisplayOrder() : 999));

        RequestTypesResponseDTO response = new RequestTypesResponseDTO(dtoList, sortedGroups);
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

    /**
     * DTO for global request type search results.
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
