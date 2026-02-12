/* rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/dto/IssueDTO.java  */

package com.samsungbuilder.jsm.dto;

import java.util.Date;

/**
 * Data Transfer Object for Jira Issues
 * Represents issue data for portal JQL table display
 */
public class IssueDTO {
    private String key;
    private String id;
    private String summary;
    private String description;
    private String status;
    private String statusId;
    private String statusIconUrl;
    private String statusCategoryKey;
    private String priority;
    private String priorityId;
    private String priorityIconUrl;
    private String issueType;
    private String issueTypeId;
    private String issueTypeIconUrl;
    private String projectKey;
    private String projectName;
    private Date created;
    private Date updated;
    private Date dueDate;
    private String reporter;
    private String reporterDisplayName;
    private String reporterEmailAddress;
    private String reporterAvatarUrl;
    private String assignee;
    private String assigneeDisplayName;
    private String assigneeEmailAddress;
    private String assigneeAvatarUrl;
    private String resolution;
    private String resolutionId;
    private Date resolutionDate;
    private String[] labels;
    private String[] components;
    private String[] fixVersions;
    private String[] affectedVersions;
    private String environment;
    private Long timeOriginalEstimate;
    private Long timeEstimate;
    private Long timeSpent;
    private Integer votes;
    private Integer watcherCount;

    // Custom fields - key is the custom field ID (e.g., "customfield_10001"), value is the display value
    private java.util.Map<String, Object> customFields;

    public IssueDTO() {
        this.customFields = new java.util.HashMap<>();
    }

    public IssueDTO(String key, String summary, String status, String priority) {
        this.key = key;
        this.summary = summary;
        this.status = status;
        this.priority = priority;
    }

    // Getters and Setters
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getStatusId() { return statusId; }
    public void setStatusId(String statusId) { this.statusId = statusId; }

    public String getStatusIconUrl() { return statusIconUrl; }
    public void setStatusIconUrl(String statusIconUrl) { this.statusIconUrl = statusIconUrl; }

    public String getStatusCategoryKey() { return statusCategoryKey; }
    public void setStatusCategoryKey(String statusCategoryKey) { this.statusCategoryKey = statusCategoryKey; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getPriorityId() { return priorityId; }
    public void setPriorityId(String priorityId) { this.priorityId = priorityId; }

    public String getPriorityIconUrl() { return priorityIconUrl; }
    public void setPriorityIconUrl(String priorityIconUrl) { this.priorityIconUrl = priorityIconUrl; }

    public String getIssueType() { return issueType; }
    public void setIssueType(String issueType) { this.issueType = issueType; }

    public String getIssueTypeId() { return issueTypeId; }
    public void setIssueTypeId(String issueTypeId) { this.issueTypeId = issueTypeId; }

    public String getIssueTypeIconUrl() { return issueTypeIconUrl; }
    public void setIssueTypeIconUrl(String issueTypeIconUrl) { this.issueTypeIconUrl = issueTypeIconUrl; }

    public String getProjectKey() { return projectKey; }
    public void setProjectKey(String projectKey) { this.projectKey = projectKey; }

    public String getProjectName() { return projectName; }
    public void setProjectName(String projectName) { this.projectName = projectName; }

    public Date getCreated() { return created; }
    public void setCreated(Date created) { this.created = created; }

    public Date getUpdated() { return updated; }
    public void setUpdated(Date updated) { this.updated = updated; }

    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }

    public String getReporter() { return reporter; }
    public void setReporter(String reporter) { this.reporter = reporter; }

    public String getReporterDisplayName() { return reporterDisplayName; }
    public void setReporterDisplayName(String reporterDisplayName) { this.reporterDisplayName = reporterDisplayName; }

    public String getReporterEmailAddress() { return reporterEmailAddress; }
    public void setReporterEmailAddress(String reporterEmailAddress) { this.reporterEmailAddress = reporterEmailAddress; }

    public String getReporterAvatarUrl() { return reporterAvatarUrl; }
    public void setReporterAvatarUrl(String reporterAvatarUrl) { this.reporterAvatarUrl = reporterAvatarUrl; }

    public String getAssignee() { return assignee; }
    public void setAssignee(String assignee) { this.assignee = assignee; }

    public String getAssigneeDisplayName() { return assigneeDisplayName; }
    public void setAssigneeDisplayName(String assigneeDisplayName) { this.assigneeDisplayName = assigneeDisplayName; }

    public String getAssigneeEmailAddress() { return assigneeEmailAddress; }
    public void setAssigneeEmailAddress(String assigneeEmailAddress) { this.assigneeEmailAddress = assigneeEmailAddress; }

    public String getAssigneeAvatarUrl() { return assigneeAvatarUrl; }
    public void setAssigneeAvatarUrl(String assigneeAvatarUrl) { this.assigneeAvatarUrl = assigneeAvatarUrl; }

    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; }

    public String getResolutionId() { return resolutionId; }
    public void setResolutionId(String resolutionId) { this.resolutionId = resolutionId; }

    public Date getResolutionDate() { return resolutionDate; }
    public void setResolutionDate(Date resolutionDate) { this.resolutionDate = resolutionDate; }

    public String[] getLabels() { return labels; }
    public void setLabels(String[] labels) { this.labels = labels; }

    public String[] getComponents() { return components; }
    public void setComponents(String[] components) { this.components = components; }

    public String[] getFixVersions() { return fixVersions; }
    public void setFixVersions(String[] fixVersions) { this.fixVersions = fixVersions; }

    public String[] getAffectedVersions() { return affectedVersions; }
    public void setAffectedVersions(String[] affectedVersions) { this.affectedVersions = affectedVersions; }

    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }

    public Long getTimeOriginalEstimate() { return timeOriginalEstimate; }
    public void setTimeOriginalEstimate(Long timeOriginalEstimate) { this.timeOriginalEstimate = timeOriginalEstimate; }

    public Long getTimeEstimate() { return timeEstimate; }
    public void setTimeEstimate(Long timeEstimate) { this.timeEstimate = timeEstimate; }

    public Long getTimeSpent() { return timeSpent; }
    public void setTimeSpent(Long timeSpent) { this.timeSpent = timeSpent; }

    public Integer getVotes() { return votes; }
    public void setVotes(Integer votes) { this.votes = votes; }

    public Integer getWatcherCount() { return watcherCount; }
    public void setWatcherCount(Integer watcherCount) { this.watcherCount = watcherCount; }

    public java.util.Map<String, Object> getCustomFields() { return customFields; }
    public void setCustomFields(java.util.Map<String, Object> customFields) { this.customFields = customFields; }

    public void setCustomField(String fieldId, Object value) {
        if (this.customFields == null) {
            this.customFields = new java.util.HashMap<>();
        }
        this.customFields.put(fieldId, value);
    }
}
