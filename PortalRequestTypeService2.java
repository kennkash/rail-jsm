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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Named
public class PortalRequestTypeService {

    private static final Logger log = LoggerFactory.getLogger(PortalRequestTypeService.class);
    private static final int PAGE_SIZE = 50;

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

    public RequestTypesResponseDTO getRequestTypesByProject(String projectKey) {
        try {
            Project project = projectManager.getProjectObjByKey(projectKey);
            if (project == null) return createEmptyResponse(projectKey);

            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
            if (serviceDesk == null) return createEmptyResponse(projectKey);

            List<RequestType> rawRequestTypes = loadRequestTypes(serviceDesk);
            return buildResponseFromRequestTypes(project, serviceDesk, rawRequestTypes);
        } catch (Exception e) {
            log.error("Error fetching request types", e);
            return createEmptyResponse(projectKey);
        }
    }

    private List<RequestType> loadRequestTypes(ServiceDesk serviceDesk) {
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
            if (response.hasNextPage()) { start += PAGE_SIZE; } else { hasMore = false; }
        }
        return requestTypes;
    }

    private RequestTypesResponseDTO buildResponseFromRequestTypes(Project project, ServiceDesk serviceDesk, List<RequestType> requestTypes) {
        List<RequestTypeDTO> dtoList = new ArrayList<>();
        Map<Integer, RequestTypeGroupDTO> groupMap = new LinkedHashMap<>();

        for (RequestType requestType : requestTypes) {
            RequestTypeDTO dto = convertToDto(requestType, project, serviceDesk);

            if (requestType.getGroups() != null) {
                for (RequestTypeGroup group : requestType.getGroups()) {
                    RequestTypeGroupDTO groupDTO = groupMap.computeIfAbsent(
                            group.getId(),
                            id -> {
                                RequestTypeGroupDTO dg = new RequestTypeGroupDTO(String.valueOf(id), group.getName());
                                group.getOrder().ifPresent(dg::setDisplayOrder);
                                dg.setRequestTypeCount(0);
                                return dg;
                            }
                    );

                    groupDTO.setRequestTypeCount(groupDTO.getRequestTypeCount() + 1);
                    dto.getGroupIds().add(String.valueOf(group.getId()));
                    dto.getGroups().add(groupDTO.getName());

                    // CAPTURE ORDER IN GROUP
                    group.getRequestTypeOrder(requestType.getId()).ifPresent(order -> {
                        dto.getGroupOrderMap().put(String.valueOf(group.getId()), order);
                        if (dto.getDisplayOrder() == null) dto.setDisplayOrder(order);
                    });
                }
            }
            dtoList.add(dto);
        }

        // SORT GROUPS BY JSM ORDER
        List<RequestTypeGroupDTO> sortedGroups = new ArrayList<>(groupMap.values());
        sortedGroups.sort(Comparator.comparingInt(g -> g.getDisplayOrder() != null ? g.getDisplayOrder() : 999));

        RequestTypesResponseDTO response = new RequestTypesResponseDTO(dtoList, sortedGroups);
        response.setProjectKey(project.getKey());
        response.setServiceDeskId(String.valueOf(serviceDesk.getId()));
        return response;
    }

    private RequestTypeDTO convertToDto(RequestType requestType, Project project, ServiceDesk serviceDesk) {
        RequestTypeDTO dto = new RequestTypeDTO();
        dto.setId(String.valueOf(requestType.getId()));
        dto.setName(requestType.getName());
        dto.setDescription(requestType.getDescription());
        dto.setProjectKey(project != null ? project.getKey() : null);
        dto.setServiceDeskId(serviceDesk != null ? String.valueOf(serviceDesk.getId()) : null);
        return dto;
    }

    private RequestTypesResponseDTO createEmptyResponse(String projectKey) {
        RequestTypesResponseDTO r = new RequestTypesResponseDTO();
        r.setProjectKey(projectKey);
        r.setRequestTypes(Collections.emptyList());
        r.setGroups(Collections.emptyList());
        return r;
    }
}
