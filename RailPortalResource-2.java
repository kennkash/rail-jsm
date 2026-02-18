    }

    /**
     * Delete portal configuration for a project
     * DELETE /rest/rail/1.0/portals/project/{projectKey}
     */
    @DELETE
    @Path("portals/project/{projectKey}")
    public Response deletePortalConfig(@PathParam("projectKey") String projectKey) {
        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            portalConfigService.deletePortalConfig(projectKey);
            log.info("Successfully deleted portal config for project: {}", projectKey);
            return Response.noContent().build();
        } catch (Exception ex) {
            log.error("Error deleting portal config for project: " + projectKey, ex);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error deleting portal config: " + ex.getMessage()))
                    .build();
        }
    }

    /**
     * DIAGNOSTIC: Get raw portal configuration without any processing
     * GET /rest/rail/1.0/diagnostic/portals/project/{projectKey}/raw
     */
    @GET
    @Path("diagnostic/portals/project/{projectKey}/raw")
    public Response getDiagnosticPortalConfigRaw(@PathParam("projectKey") String projectKey) {
        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(projectKey);

            Map<String, Object> diagnostic = new HashMap<>();
            diagnostic.put("projectKey", projectKey);
            diagnostic.put("configExists", config.isPresent());

            if (config.isPresent()) {
                PortalConfigDTO dto = config.get();
                diagnostic.put("hasComponents", dto.getComponents() != null);
                diagnostic.put("componentsIsEmpty", dto.getComponents() != null && dto.getComponents().isEmpty());
                diagnostic.put("componentCount", dto.getComponents() != null ? dto.getComponents().size() : 0);
                diagnostic.put("isLive", dto.isLive());
                diagnostic.put("portalId", dto.getPortalId());
                diagnostic.put("projectName", dto.getProjectName());
                diagnostic.put("fullConfig", dto);
            }

            return Response.ok(diagnostic).build();

        } catch (Exception e) {
            log.error("Error in diagnostic endpoint for project: " + projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Diagnostic failed: " + e.getMessage()))
                    .build();
        }
    }

    // ==================== Request Types Endpoints ====================

    /**
     * Get all request types for a specific project
     * GET /rest/rail/1.0/projects/{projectKey}/request-types
     */
    @GET
    @Path("projects/{projectKey}/request-types")
    public Response getRequestTypes(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}/request-types", projectKey);

        try {
            RequestTypesResponseDTO response = requestTypeService.getRequestTypesByProject(projectKey);

            if (response.getRequestTypes().isEmpty()) {
                log.warn("No request types found for project: {}", projectKey);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("No request types found for project: " + projectKey))
                        .build();
            }

            log.debug("Returning {} request types for project {}", response.getRequestTypes().size(), projectKey);
            if (!response.getRequestTypes().isEmpty()) {
                RequestTypeDTO first = response.getRequestTypes().get(0);
                log.debug("First request type sample - id: {}, name: {}, group: {}, enabled: {}",
                        first.getId(), first.getName(), first.getGroup(), first.isEnabled());
            }

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching request types for project: " + projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching request types: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get grouped request types for a project
     * GET /rest/rail/1.0/projects/{projectKey}/request-types/grouped
     */
    @GET
    @Path("projects/{projectKey}/request-types/grouped")
    public Response getGroupedRequestTypes(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}/request-types/grouped", projectKey);

        try {
            Map<String, List<RequestTypeDTO>> grouped = requestTypeService.getGroupedRequestTypes(projectKey);
            return Response.ok(grouped).build();

        } catch (Exception e) {
            log.error("Error fetching grouped request types for project: " + projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching grouped request types: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get a single request type by ID
     * GET /rest/rail/1.0/request-types/{requestTypeId}
     */
    @GET
    @Path("request-types/{requestTypeId}")
    public Response getRequestType(@PathParam("requestTypeId") String requestTypeId) {
        log.debug("GET /request-types/{}", requestTypeId);

        try {
            RequestTypeDTO requestType = requestTypeService.getRequestTypeById(requestTypeId);

            if (requestType == null) {
                log.warn("Request type not found: {}", requestTypeId);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Request type not found: " + requestTypeId))
                        .build();
            }

            return Response.ok(requestType).build();

        } catch (Exception e) {
            log.error("Error fetching request type: " + requestTypeId, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching request type: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Search request types
     * GET /rest/rail/1.0/projects/{projectKey}/request-types/search?q=term
     */
    @GET
    @Path("projects/{projectKey}/request-types/search")
    public Response searchRequestTypes(
            @PathParam("projectKey") String projectKey,
            @QueryParam("q") String searchTerm) {
        log.debug("GET /projects/{}/request-types/search?q={}", projectKey, searchTerm);

        try {
            List<RequestTypeDTO> results = requestTypeService.searchRequestTypes(projectKey, searchTerm);
            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("count", results.size());
            response.put("searchTerm", searchTerm);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error searching request types", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error searching request types: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Global search for request types across all service desk projects
     * GET /rest/rail/1.0/request-types/search?q=term&limit=20
     */
    @GET
    @Path("request-types/search")
    public Response searchAllRequestTypes(
            @QueryParam("q") String searchTerm,
            @QueryParam("limit") @DefaultValue("20") int limit) {
        log.debug("GET /request-types/search?q={}&limit={}", searchTerm, limit);

        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Search term is required"))
                    .build();
        }

        try {
            int maxResults = Math.min(Math.max(limit, 1), 50); // Clamp between 1 and 50
            List<PortalRequestTypeService.GlobalRequestTypeSearchResult> results =
                    requestTypeService.searchAllRequestTypes(searchTerm, maxResults);

            Map<String, Object> response = new HashMap<>();
            response.put("results", results);
            response.put("count", results.size());
            response.put("searchTerm", searchTerm);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error performing global request type search", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error searching request types: " + e.getMessage()))
                    .build();
        }
    }

    // ==================== Projects Endpoints ====================

    /**
     * Get all service desk projects
     * GET /rest/rail/1.0/projects
     */
    @GET
    @Path("projects")
    public Response getProjects() {
        log.debug("GET /projects");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            List<ProjectDTO> projects = projectService.getAllServiceDeskProjects(currentUser);

            log.debug("Retrieved {} projects from service", projects.size());
            if (!projects.isEmpty()) {
                ProjectDTO first = projects.get(0);
                log.debug("First project sample - id: {}, key: {}, name: {}, desc: {}, serviceDesk: {}",
                        first.getId(), first.getKey(), first.getName(),
                        first.getDescription() != null ? first.getDescription().substring(0, Math.min(30, first.getDescription().length())) : "null",
                        first.isServiceDesk());
            }

            Map<String, Object> response = new HashMap<>();
            response.put("projects", projects);
            response.put("count", projects.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching projects", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching projects: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get a single project by key
     * GET /rest/rail/1.0/projects/{projectKey}
     */
    @GET
    @Path("projects/{projectKey}")
    public Response getProject(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}", projectKey);

        try {
            ProjectDTO project = projectService.getProjectByKey(projectKey);

            if (project == null) {
                log.warn("Project not found: {}", projectKey);
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Project not found: " + projectKey))
                        .build();
            }

            log.debug("Returning project - id: {}, key: {}, name: {}, serviceDesk: {}",
                    project.getId(), project.getKey(), project.getName(), project.isServiceDesk());

            return Response.ok(project).build();

        } catch (Exception e) {
            log.error("Error fetching project: " + projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Search projects
     * GET /rest/rail/1.0/projects/search?q=term
     */
    @GET
    @Path("projects/search")
    public Response searchProjects(@QueryParam("q") String searchTerm) {
        log.debug("GET /projects/search?q={}", searchTerm);

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            List<ProjectDTO> results = projectService.searchProjects(searchTerm, currentUser);

            Map<String, Object> response = new HashMap<>();
            response.put("projects", results);
            response.put("count", results.size());
            response.put("searchTerm", searchTerm);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error searching projects", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error searching projects: " + e.getMessage()))
                    .build();
        }
    }





    /**
     * Get available project roles
     * GET /rest/rail/1.0/projects/{projectKey}/roles
     */
    @GET
    @Path("projects/{projectKey}/roles")
    public Response getProjectRoles(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}/roles", projectKey);

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("User not authenticated"))
                        .build();
            }

            var projectManager = ComponentAccessor.getProjectManager();
            var project = projectManager.getProjectObjByKey(projectKey);

            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Project not found: " + projectKey))
                        .build();
            }

            // Check if user can see the project
            var permissionManager = ComponentAccessor.getPermissionManager();
            if (!permissionManager.hasPermission(com.atlassian.jira.security.Permissions.BROWSE, project, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            List<Map<String, Object>> roles = new ArrayList<>();
            var projectRoleManager = ComponentAccessor.getComponent(com.atlassian.jira.security.roles.ProjectRoleManager.class);
            var projectRoles = projectRoleManager.getProjectRoles();

            for (var role : projectRoles) {
                Map<String, Object> roleMap = new HashMap<>();
                roleMap.put("id", role.getId().toString());
                roleMap.put("name", role.getName());
                roleMap.put("description", role.getDescription());

                // Get actors count for this project
                var roleActors = projectRoleManager.getProjectRoleActors(role, project);
                if (roleActors != null) {
                    roleMap.put("memberCount", roleActors.getUsers().size());
                }

                roles.add(roleMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("roles", roles);
            response.put("count", roles.size());
            response.put("projectKey", projectKey);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching project roles: projectKey={}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project roles: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get available fields for a project (for JQL table column selection)
     * GET /rest/rail/1.0/projects/{projectKey}/fields
     *
     * Returns system fields and custom fields that can be displayed in the JQL table.
     */
    @GET
    @Path("projects/{projectKey}/fields")
    public Response getProjectFields(@PathParam("projectKey") String projectKey) {
        log.debug("GET /projects/{}/fields", projectKey);

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("User not authenticated"))
                        .build();
            }

            var projectManager = ComponentAccessor.getProjectManager();
            var project = projectManager.getProjectObjByKey(projectKey);

            if (project == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(createErrorResponse("Project not found: " + projectKey))
                        .build();
            }

            // Check if user can see the project
            var permissionManager = ComponentAccessor.getPermissionManager();
            if (!permissionManager.hasPermission(com.atlassian.jira.security.Permissions.BROWSE, project, currentUser)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            // Build list of system fields (these are always available)
            List<Map<String, Object>> systemFields = new ArrayList<>();
            systemFields.add(createFieldInfo("key", "Key", "system", false));
            systemFields.add(createFieldInfo("summary", "Summary", "system", false));
            systemFields.add(createFieldInfo("status", "Status", "system", false));
            systemFields.add(createFieldInfo("priority", "Priority", "system", false));
            systemFields.add(createFieldInfo("issueType", "Issue Type", "system", false));
            systemFields.add(createFieldInfo("created", "Created", "system", false));
            systemFields.add(createFieldInfo("updated", "Updated", "system", false));
            systemFields.add(createFieldInfo("dueDate", "Due Date", "system", false));
            systemFields.add(createFieldInfo("reporter", "Reporter", "system", false));
            systemFields.add(createFieldInfo("assignee", "Assignee", "system", false));
            systemFields.add(createFieldInfo("resolution", "Resolution", "system", false));
            systemFields.add(createFieldInfo("labels", "Labels", "system", false));
            systemFields.add(createFieldInfo("components", "Components", "system", false));
            systemFields.add(createFieldInfo("fixVersions", "Fix Versions", "system", false));
            systemFields.add(createFieldInfo("affectedVersions", "Affected Versions", "system", false));

            // Get custom fields for the project
            List<Map<String, Object>> customFields = new ArrayList<>();
            try {
                var customFieldManager = ComponentAccessor.getCustomFieldManager();
                if (customFieldManager != null) {
                    var allCustomFields = customFieldManager.getCustomFieldObjects();
                    for (var cf : allCustomFields) {

                        // Determine applicability by inspecting ALL field config schemes (contexts)
                        // A global context is represented by an empty associated-project list on a scheme.
                        boolean isForProject = isCustomFieldApplicableToProject(cf, project);

                        if (isForProject) {
                            String cfId = cf.getId(); // e.g., "customfield_10001"
                            String cfName = cf.getName();
                            String cfType = cf.getCustomFieldType() != null
                                    ? cf.getCustomFieldType().getName()
                                    : "unknown";

                            Map<String, Object> fieldInfo = createFieldInfo(cfId, cfName, "custom", true);
                            fieldInfo.put("customFieldType", cfType);
                            customFields.add(fieldInfo);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Error fetching custom fields for project {}: {}", projectKey, e.getMessage());
                // Continue without custom fields
            }

            Map<String, Object> response = new HashMap<>();
            response.put("projectKey", projectKey);
            response.put("systemFields", systemFields);
            response.put("customFields", customFields);
            response.put("totalFields", systemFields.size() + customFields.size());

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error fetching project fields: projectKey={}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project fields: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Helper: context-aware custom field applicability for a project.
     *
     * Why this exists:
     * - cf.getAssociatedProjectObjects() can be misleading when a field has multiple contexts
     *   (e.g., one global + one or more project-scoped contexts).
     * - This method returns true if ANY scheme is global OR explicitly includes the project.
     */
    private boolean isCustomFieldApplicableToProject(CustomField cf, Project project) {
        try {
            @SuppressWarnings("unchecked")
            List<FieldConfigScheme> schemes = (List<FieldConfigScheme>) cf.getConfigurationSchemes();

            if (schemes == null || schemes.isEmpty()) {
                return false;
            }

            for (FieldConfigScheme scheme : schemes) {
                List<Project> associated = scheme.getAssociatedProjectObjects();

                // Global context: applies to ALL projects
                if (associated == null || associated.isEmpty()) {
                    return true;
                }

                // Project-scoped context
                for (Project p : associated) {
                    if (p != null && p.getId() != null && p.getId().equals(project.getId())) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.warn("Failed to evaluate context schemes for custom field {}: {}", cf.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * Helper method to create field info map
     */
    private Map<String, Object> createFieldInfo(String id, String name, String category, boolean isCustom) {
        Map<String, Object> field = new HashMap<>();
        field.put("id", id);
        field.put("name", name);
        field.put("category", category);
        field.put("isCustom", isCustom);
        return field;
    }

    /**
     * Evaluate visibility rules for current user
     * POST /rest/rail/1.0/visibility/evaluate
     *
     * Request body:
     * {
     *   "conditions": [
     *     {"type": "user", "operator": "is", "value": "jsmith"},
     *     {"type": "group", "operator": "in", "value": ["jira-administrators"]}
     *   ],
     *   "logic": "AND",
     *   "mode": "show",
     *   "projectKey": "DEMO"
     * }
     */
    @POST
    @Path("visibility/evaluate")
    public Response evaluateVisibility(Map<String, Object> requestBody) {
        log.debug("POST /visibility/evaluate");

        try {
            ApplicationUser currentUser = authenticationContext.getLoggedInUser();
            if (currentUser == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(createErrorResponse("User not authenticated"))
                        .build();
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) requestBody.get("conditions");
            String logic = (String) requestBody.getOrDefault("logic", "AND");
            String mode = (String) requestBody.getOrDefault("mode", "show");
            String projectKey = (String) requestBody.get("projectKey");

            if (conditions == null || conditions.isEmpty()) {
                Map<String, Object> response = new HashMap<>();
                response.put("visible", true);
                response.put("matchedConditions", 0);
                response.put("totalConditions", 0);
                return Response.ok(response).build();
            }

            int matchedCount = 0;
            int totalCount = conditions.size();

            for (Map<String, Object> condition : conditions) {
                String type = (String) condition.get("type");
                String operator = (String) condition.get("operator");
                Object value = condition.get("value");

                boolean conditionMatches = evaluateCondition(currentUser, type, operator, value, projectKey);

                if (conditionMatches) {
                    matchedCount++;
                }

                // Short-circuit evaluation
                if ("AND".equalsIgnoreCase(logic) && !conditionMatches) {
                    break; // One failure in AND means overall failure
                }
                if ("OR".equalsIgnoreCase(logic) && conditionMatches) {
                    break; // One success in OR means overall success
                }
            }

            // Determine if conditions are met
            boolean conditionsMet;
            if ("AND".equalsIgnoreCase(logic)) {
                conditionsMet = matchedCount == totalCount;
            } else {
                conditionsMet = matchedCount > 0;
            }

            // Apply mode (show vs hide)
            boolean visible;
            if ("show".equalsIgnoreCase(mode)) {
                visible = conditionsMet;
            } else { // hide mode
                visible = !conditionsMet;
            }

            Map<String, Object> response = new HashMap<>();
            response.put("visible", visible);
            response.put("matchedConditions", matchedCount);
            response.put("totalConditions", totalCount);
            response.put("logic", logic);
            response.put("mode", mode);

            return Response.ok(response).build();

        } catch (Exception e) {
            log.error("Error evaluating visibility", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error evaluating visibility: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Helper method to evaluate a single visibility condition
     */
    private boolean evaluateCondition(ApplicationUser user, String type, String operator, Object value, String projectKey) {
        try {
            switch (type.toLowerCase()) {
                case "user":
                    return evaluateUserCondition(user, operator, value);

                case "group":
                    return evaluateGroupCondition(user, operator, value);

                case "role":
                    return evaluateRoleCondition(user, operator, value, projectKey);

                default:
                    log.warn("Unknown condition type: {}", type);
                    return false;
            }
        } catch (Exception e) {
            log.error("Error evaluating condition: type={}, operator={}", type, operator, e);
            return false;
        }
    }

    private boolean evaluateUserCondition(ApplicationUser user, String operator, Object value) {
        String username = user.getUsername();

        // Handle "is" operator - can be String or List containing single username
        if ("is".equalsIgnoreCase(operator)) {
            if (value instanceof String) {
                return username.equals(value);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> usernames = (List<String>) value;
                // For "is", typically single value but handle list scenario
                return usernames.size() == 1 && username.equals(usernames.get(0));
            }
            return false;
        }
        // Handle "is-not" operator - opposite of "is"
        else if ("is-not".equalsIgnoreCase(operator)) {
            if (value instanceof String) {
                return !username.equals(value);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> usernames = (List<String>) value;
                // For "is-not", user must not match the single username
                return usernames.size() == 1 && !username.equals(usernames.get(0));
            }
            return true; // If no value, consider it as "is-not" match
        }
        // Handle "in" operator - username is in the list
        else if ("in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> usernames = (List<String>) value;
            return usernames.contains(username);
        }
        // Handle "not-in" operator - username is not in the list
        else if ("not-in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> usernames = (List<String>) value;
            return !usernames.contains(username);
        }

        return false;
    }

    private boolean evaluateGroupCondition(ApplicationUser user, String operator, Object value) {
        var groupManager = ComponentAccessor.getGroupManager();

        // Handle "is" operator - user is in exactly one group (for single-select UI)
        if ("is".equalsIgnoreCase(operator)) {
            if (value instanceof String) {
                var group = groupManager.getGroup((String) value);
                return group != null && groupManager.isUserInGroup(user, group);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> groupNames = (List<String>) value;
                // For "is" with list, typically single group but handle list scenario
                if (groupNames.size() == 1) {
                    var group = groupManager.getGroup(groupNames.get(0));
                    return group != null && groupManager.isUserInGroup(user, group);
                }
                return false;
            }
            return false;
        }
        // Handle "is-not" operator - opposite of "is"
        else if ("is-not".equalsIgnoreCase(operator)) {
            if (value instanceof String) {
                var group = groupManager.getGroup((String) value);
                return group == null || !groupManager.isUserInGroup(user, group);
            } else if (value instanceof List) {
                @SuppressWarnings("unchecked")
                List<String> groupNames = (List<String>) value;
                // For "is-not" with list, user must not be in the single group
                if (groupNames.size() == 1) {
                    var group = groupManager.getGroup(groupNames.get(0));
                    return group == null || !groupManager.isUserInGroup(user, group);
                }
                return true;
            }
            return true;
        }
        // Handle "in" operator - user is in any of the groups (multi-select)
        else if ("in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> groupNames = (List<String>) value;

            for (String groupName : groupNames) {
                var group = groupManager.getGroup(groupName);
                if (group != null && groupManager.isUserInGroup(user, group)) {
                    return true;
                }
            }
            return false;
        }
        // Handle "in" operator with single string value
        else if ("in".equalsIgnoreCase(operator) && value instanceof String) {
            var group = groupManager.getGroup((String) value);
            return group != null && groupManager.isUserInGroup(user, group);
        }
        // Handle "not-in" operator - user is not in any of the groups (multi-select)
        else if ("not-in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> groupNames = (List<String>) value;

            for (String groupName : groupNames) {
                var group = groupManager.getGroup(groupName);
                if (group != null && groupManager.isUserInGroup(user, group)) {
                    return false;
                }
            }
            return true;
        }
        // Handle "not-in" operator with single string value
        else if ("not-in".equalsIgnoreCase(operator) && value instanceof String) {
            var group = groupManager.getGroup((String) value);
            return group == null || !groupManager.isUserInGroup(user, group);
        }

        return false;
    }

    private boolean evaluateRoleCondition(ApplicationUser user, String operator, Object value, String projectKey) {
        if (projectKey == null || projectKey.trim().isEmpty()) {
            log.warn("Project key required for role condition evaluation");
            return false;
        }

        var projectManager = ComponentAccessor.getProjectManager();
        var project = projectManager.getProjectObjByKey(projectKey);
        if (project == null) {
            log.warn("Project not found: {}", projectKey);
            return false;
        }

        var projectRoleManager = ComponentAccessor.getComponent(com.atlassian.jira.security.roles.ProjectRoleManager.class);

        if ("in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roleNames = (List<String>) value;

            for (String roleName : roleNames) {
                var role = projectRoleManager.getProjectRole(roleName);
                if (role != null) {
                    var roleActors = projectRoleManager.getProjectRoleActors(role, project);
                    if (roleActors != null && roleActors.getUsers().contains(user)) {
                        return true;
                    }
                }
            }
            return false;

        } else if ("not-in".equalsIgnoreCase(operator) && value instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> roleNames = (List<String>) value;

            for (String roleName : roleNames) {
                var role = projectRoleManager.getProjectRole(roleName);
                if (role != null) {
                    var roleActors = projectRoleManager.getProjectRoleActors(role, project);
                    if (roleActors != null && roleActors.getUsers().contains(user)) {
                        return false;
                    }
                }
            }
            return true;

        } else if ("in".equalsIgnoreCase(operator) && value instanceof String) {
            var role = projectRoleManager.getProjectRole((String) value);
            if (role != null) {
                var roleActors = projectRoleManager.getProjectRoleActors(role, project);
                return roleActors != null && roleActors.getUsers().contains(user);
            }
        } else if ("not-in".equalsIgnoreCase(operator) && value instanceof String) {
            var role = projectRoleManager.getProjectRole((String) value);
            if (role != null) {
                var roleActors = projectRoleManager.getProjectRoleActors(role, project);
                return roleActors == null || !roleActors.getUsers().contains(user);
            }
            return true;
        }

        return false;
    }

    /**
     * DIAGNOSTIC ENDPOINT: Test DTO serialization with hardcoded values
     * GET /rest/rail/1.0/diagnostic/test-dto
     */
    @GET
    @Path("diagnostic/test-dto")
    public Response testDtoSerialization() {
        log.info("DIAGNOSTIC: Testing DTO serialization");

        // Create a DTO with ALL fields populated
        ProjectDTO dto = new ProjectDTO();
        dto.setId("99999");
        dto.setKey("TEST");
        dto.setName("Test Project Name");
        dto.setDescription("Test project description for diagnostic purposes");
        dto.setProjectTypeKey("service_desk");
        dto.setAvatarUrl("https://example.com/avatar.png");
        dto.setServiceDeskId("12345");
        dto.setPortalId("67890");
        dto.setPortalName("Test Portal");
        dto.setRequestTypeCount(5);
        dto.setServiceDesk(true);

        log.info("DIAGNOSTIC: Created DTO with - id:{}, key:{}, name:{}, desc:{}, typeKey:{}, avatarUrl:{}, serviceDesk:{}",
                dto.getId(), dto.getKey(), dto.getName(), dto.getDescription(),
                dto.getProjectTypeKey(), dto.getAvatarUrl(), dto.isServiceDesk());

        return Response.ok(dto).build();
    }

    /**
     * DIAGNOSTIC ENDPOINT: Test DTO serialization as Map
     * GET /rest/rail/1.0/diagnostic/test-map
     */
    @GET
    @Path("diagnostic/test-map")
    public Response testMapSerialization() {
        log.info("DIAGNOSTIC: Testing Map serialization");

        Map<String, Object> map = new HashMap<>();
        map.put("id", "99999");
        map.put("key", "TEST");
        map.put("name", "Test Project Name");
        map.put("description", "Test project description");
        map.put("projectTypeKey", "service_desk");
        map.put("avatarUrl", "https://example.com/avatar.png");
        map.put("serviceDesk", true);

        return Response.ok(map).build();
    }

    // ==================== Issue Search Endpoints ====================

    /**
     * Search issues using JQL query with optional server-side search and filter.
     *
     * Server-side search/filter enables searching across the ENTIRE result set,
     * not just the current page. This fixes the issue where search only worked
     * on visible issues.
     *
     * GET /rest/rail/1.0/issues/search?jql=query&start=0&limit=25&search=term&status=Open,Done&priority=High
     *
     * @param jqlQuery The JQL query (required)
     * @param startIndex Pagination start index (default: 0)
     * @param pageSize Number of results per page (default: 25)
     * @param searchTerm Optional text search (searches key, summary, description)
     * @param statusFilter Optional comma-separated status names to filter by
     * @param priorityFilter Optional comma-separated priority names to filter by
     */
    @GET
    @Path("issues/search")
    public Response searchIssues(
            @QueryParam("jql") String jqlQuery,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize,
            @QueryParam("search") String searchTerm,
            @QueryParam("status") String statusFilter,
            @QueryParam("priority") String priorityFilter,
            @QueryParam("sortField") String sortField,
            @QueryParam("sortDir") @DefaultValue("asc") String sortDir,
            @QueryParam("facets") @DefaultValue("false") boolean includeFacets) {
        log.debug("GET /issues/search?jql={}&start={}&limit={}&search={}&status={}&priority={}",
                  jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter);

        if (jqlQuery == null || jqlQuery.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("JQL query parameter is required"))
                    .build();
        }

        try {
            IssueSearchResponseDTO response = issueService.searchIssues(
                    jqlQuery, startIndex, pageSize, searchTerm, statusFilter, priorityFilter, sortField, sortDir, includeFacets);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid JQL query: {}", jqlQuery, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Invalid JQL query: " + e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Error searching issues with JQL: {}", jqlQuery, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Issue search failed: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get issues for a specific project (current user's issues)
     * GET /rest/rail/1.0/projects/{projectKey}/issues?start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues")
    public Response getProjectIssues(
            @PathParam("projectKey") String projectKey,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues?start={}&limit={}", projectKey, startIndex, pageSize);

        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchProjectIssues(projectKey, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (Exception e) {
            log.error("Error fetching issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project issues: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all issues for a specific project (that user can see)
     * GET /rest/rail/1.0/projects/{projectKey}/issues/all?start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues/all")
    public Response getAllProjectIssues(
            @PathParam("projectKey") String projectKey,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues/all?start={}&limit={}", projectKey, startIndex, pageSize);
