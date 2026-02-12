 /**
     * Convert IssueDTO to Map for safe JSON serialization.
     */
    private Map<String, Object> convertIssueDtoToMap(IssueDTO issue) {
        Map<String, Object> map = new HashMap<>();
        map.put("key", issue.getKey());
        map.put("id", issue.getId());
        map.put("summary", issue.getSummary());
        map.put("description", issue.getDescription());
        map.put("status", issue.getStatus());
        map.put("statusId", issue.getStatusId());
        map.put("statusIconUrl", issue.getStatusIconUrl());
        map.put("priority", issue.getPriority());
        map.put("priorityId", issue.getPriorityId());
        map.put("priorityIconUrl", issue.getPriorityIconUrl());
        map.put("issueType", issue.getIssueType());
        map.put("issueTypeId", issue.getIssueTypeId());
        map.put("issueTypeIconUrl", issue.getIssueTypeIconUrl());
        map.put("projectKey", issue.getProjectKey());
        map.put("projectName", issue.getProjectName());
        map.put("created", issue.getCreated());
        map.put("updated", issue.getUpdated());
        map.put("dueDate", issue.getDueDate());
        map.put("reporter", issue.getReporter());
        map.put("reporterDisplayName", issue.getReporterDisplayName());
        map.put("reporterEmailAddress", issue.getReporterEmailAddress());
        map.put("reporterAvatarUrl", issue.getReporterAvatarUrl());
        map.put("assignee", issue.getAssignee());
        map.put("assigneeDisplayName", issue.getAssigneeDisplayName());
        map.put("assigneeEmailAddress", issue.getAssigneeEmailAddress());
        map.put("assigneeAvatarUrl", issue.getAssigneeAvatarUrl());
        map.put("resolution", issue.getResolution());
        map.put("resolutionId", issue.getResolutionId());
        map.put("resolutionDate", issue.getResolutionDate());
        map.put("labels", issue.getLabels());
        map.put("components", issue.getComponents());
        map.put("fixVersions", issue.getFixVersions());
        map.put("affectedVersions", issue.getAffectedVersions());
        map.put("environment", issue.getEnvironment());
        map.put("timeOriginalEstimate", issue.getTimeOriginalEstimate());
        map.put("timeEstimate", issue.getTimeEstimate());
        map.put("timeSpent", issue.getTimeSpent());
        map.put("votes", issue.getVotes());
        map.put("watcherCount", issue.getWatcherCount());
        // Include custom fields
        map.put("customFields", issue.getCustomFields());
        return map;
    }
