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
import com.atlassian.servicedesk.api.util.paging.PagedResponse;
import com.atlassian.servicedesk.api.util.paging.SimplePagedRequest;
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
import java.util.Map;

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

    @Inject
    public PortalRequestTypeService(
            @ComponentImport ProjectManager projectManager,
            @ComponentImport ServiceDeskManager serviceDeskManager,
            @ComponentImport com.atlassian.servicedesk.api.requesttype.RequestTypeService requestTypeService,
            @ComponentImport JiraAuthenticationContext authenticationContext
    ) {
        this.projectManager = projectManager;
        this.serviceDeskManager = serviceDeskManager;
        this.jsdRequestTypeService = requestTypeService;
        this.authenticationContext = authenticationContext;
    }

    /**
     * Get all request types for a specific project
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

        // 2. CRITICAL FIX: Query each group individually to get the CORRECT JSM Portal Order
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
}
