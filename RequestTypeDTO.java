package com.samsungbuilder.jsm.dto;

import org.codehaus.jackson.annotate.JsonProperty;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestTypeDTO {

    @JsonProperty("id")
    private String id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("icon")
    private String icon;

    @JsonProperty("iconUrl")
    private String iconUrl;

    @JsonProperty("color")
    private String color;

    @JsonProperty("group")
    private String group;

    @JsonProperty("groupIds")
    private List<String> groupIds;

    @JsonProperty("groups")
    private List<String> groups;

    @JsonProperty("projectKey")
    private String projectKey;

    @JsonProperty("issueTypeId")
    private String issueTypeId;

    @JsonProperty("serviceDeskId")
    private String serviceDeskId;

    @JsonProperty("portalId")
    private String portalId;

    @JsonProperty("helpText")
    private String helpText;

    @JsonProperty("fields")
    private List<String> fields;

    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("displayOrder")
    private Integer displayOrder;

    @JsonProperty("groupOrderMap")
    private Map<String, Integer> groupOrderMap;

    public RequestTypeDTO() {
        this.groupIds = new ArrayList<>();
        this.groups = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.groupOrderMap = new HashMap<>();
        this.enabled = true;
    }

    public RequestTypeDTO(String id, String name, String description) {
        this();
        this.id = id;
        this.name = name;
        this.description = description;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getGroup() { return group; }
    public void setGroup(String group) { this.group = group; }
    public List<String> getGroupIds() { return groupIds; }
    public void setGroupIds(List<String> groupIds) { this.groupIds = groupIds; }
    public List<String> getGroups() { return groups; }
    public void setGroups(List<String> groups) { this.groups = groups; }
    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }
    public String getIssueTypeId() { return issueTypeId; }
    public void setIssueTypeId(String issueTypeId) { this.issueTypeId = issueTypeId; }
    public String getServiceDeskId() { return serviceDeskId; }
    public void setServiceDeskId(String serviceDeskId) { this.serviceDeskId = serviceDeskId; }
    public String getPortalId() { return portalId; }
    public void setPortalId(String portalId) { this.portalId = portalId; }
    public String getHelpText() { return helpText; }
    public void setHelpText(String helpText) { this.helpText = helpText; }
    public List<String> getFields() { return fields; }
    public void setFields(List<String> fields) { this.fields = fields; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Map<String, Integer> getGroupOrderMap() { return groupOrderMap; }
    public void setGroupOrderMap(Map<String, Integer> groupOrderMap) { this.groupOrderMap = groupOrderMap; }
}
