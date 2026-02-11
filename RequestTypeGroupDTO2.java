package com.samsungbuilder.jsm.dto;

import org.codehaus.jackson.annotate.JsonProperty;

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

    public RequestTypeGroupDTO() {}

    public RequestTypeGroupDTO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Integer getRequestTypeCount() { return requestTypeCount; }
    public void setRequestTypeCount(Integer requestTypeCount) { this.requestTypeCount = requestTypeCount; }
}
