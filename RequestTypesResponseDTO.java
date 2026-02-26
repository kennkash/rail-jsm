// rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/dto/RequestTypesResponseDTO.java
package com.samsungbuilder.jsm.dto;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Request Types API Response
 * Wraps request types and their groups for comprehensive portal display
 */
public class RequestTypesResponseDTO {

    @JsonProperty("projectKey")
    private String projectKey;

    @JsonProperty("projectName")
    private String projectName;

    @JsonProperty("serviceDeskId")
    private String serviceDeskId;

    @JsonProperty("requestTypes")
    private List<RequestTypeDTO> requestTypes;

    @JsonProperty("groups")
    private List<RequestTypeGroupDTO> groups;

    @JsonProperty("totalCount")
    private int totalCount;

    @JsonProperty("hasGroups")
    private boolean hasGroups;

    public RequestTypesResponseDTO() {
        this.requestTypes = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.totalCount = 0;
        this.hasGroups = false;
    }

    public RequestTypesResponseDTO(List<RequestTypeDTO> requestTypes) {
        this();
        this.requestTypes = requestTypes;
        this.totalCount = requestTypes != null ? requestTypes.size() : 0;
    }

    public RequestTypesResponseDTO(List<RequestTypeDTO> requestTypes, List<RequestTypeGroupDTO> groups) {
        this(requestTypes);
        this.groups = groups;
        this.hasGroups = groups != null && !groups.isEmpty();
    }

    // Getters and Setters

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getServiceDeskId() {
        return serviceDeskId;
    }

    public void setServiceDeskId(String serviceDeskId) {
        this.serviceDeskId = serviceDeskId;
    }

    public List<RequestTypeDTO> getRequestTypes() {
        return requestTypes;
    }

    public void setRequestTypes(List<RequestTypeDTO> requestTypes) {
        this.requestTypes = requestTypes;
        this.totalCount = requestTypes != null ? requestTypes.size() : 0;
    }

    public List<RequestTypeGroupDTO> getGroups() {
        return groups;
    }

    public void setGroups(List<RequestTypeGroupDTO> groups) {
        this.groups = groups;
        this.hasGroups = groups != null && !groups.isEmpty();
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }

    public boolean isHasGroups() {
        return hasGroups;
    }

    public void setHasGroups(boolean hasGroups) {
        this.hasGroups = hasGroups;
    }

    @Override
    public String toString() {
        return "RequestTypesResponseDTO{" +
                "projectKey='" + projectKey + '\'' +
                ", totalCount=" + totalCount +
                ", hasGroups=" + hasGroups +
                '}';
    }
}
