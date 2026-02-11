/* /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/dto/RequestTypeGroupDTO.java */
package com.samsungbuilder.jsm.dto;

import org.codehaus.jackson.annotate.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Data Transfer Object for Request Type Groups
 * Represents a grouping/category of request types in Jira Service Management
 */
public class RequestTypeGroupDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("serviceDeskId")
    private String serviceDeskId;

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("requestTypeCount")
    private Integer requestTypeCount;

    // ✅ NEW: ordered request types for THIS group
    @JsonProperty("requestTypes")
    private List<RequestTypeDTO> requestTypes = new ArrayList<>();

    public RequestTypeGroupDTO() {
    }

    public RequestTypeGroupDTO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public RequestTypeGroupDTO(String id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getServiceDeskId() {
        return serviceDeskId;
    }

    public void setServiceDeskId(String serviceDeskId) {
        this.serviceDeskId = serviceDeskId;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    public Integer getRequestTypeCount() {
        return requestTypeCount;
    }

    public void setRequestTypeCount(Integer requestTypeCount) {
        this.requestTypeCount = requestTypeCount;
    }

    public List<RequestTypeDTO> getRequestTypes() {
        return requestTypes;
    }

    public void setRequestTypes(List<RequestTypeDTO> requestTypes) {
        this.requestTypes = requestTypes != null ? requestTypes : new ArrayList<>();
        this.requestTypeCount = this.requestTypes.size();
    }

    public void addRequestType(RequestTypeDTO dto) {
        if (dto == null) return;
        this.requestTypes.add(dto);
        this.requestTypeCount = this.requestTypes.size();
    }

    @Override
    public String toString() {
        return "RequestTypeGroupDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", requestTypeCount=" + requestTypeCount +
                '}';
    }
}