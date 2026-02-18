        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchAllProjectIssues(projectKey, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (Exception e) {
            log.error("Error fetching all issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching project issues: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Get issues for a specific project with additional filter
     * GET /rest/rail/1.0/projects/{projectKey}/issues/filter?filter=status=Open&start=0&limit=25
     */
    @GET
    @Path("projects/{projectKey}/issues/filter")
    public Response getProjectIssuesWithFilter(
            @PathParam("projectKey") String projectKey,
            @QueryParam("filter") String additionalFilter,
            @QueryParam("start") @DefaultValue("0") int startIndex,
            @QueryParam("limit") @DefaultValue("25") int pageSize) {
        log.debug("GET /projects/{}/issues/filter?filter={}&start={}&limit={}", projectKey, additionalFilter, startIndex, pageSize);

        try {
            // Check if user can see the project
            if (!issueService.canUserSeeProject(projectKey)) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(createErrorResponse("Access denied to project: " + projectKey))
                        .build();
            }

            IssueSearchResponseDTO response = issueService.searchProjectIssuesWithFilter(projectKey, additionalFilter, startIndex, pageSize);
            return Response.ok(convertIssueSearchResponseToMap(response)).build();

        } catch (IllegalArgumentException e) {
            log.warn("Invalid filter for project {}: {}", projectKey, additionalFilter, e);
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Invalid filter: " + e.getMessage()))
                    .build();

        } catch (Exception e) {
            log.error("Error fetching filtered issues for project: {}", projectKey, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Error fetching filtered project issues: " + e.getMessage()))
                    .build();
        }
    }

    /**
     * Proxy Echo AI calls server-side to avoid browser CORS on OAuth/token endpoints.
     * POST /rest/rail/1.0/echo
     */
    @POST
    @Path("echo")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response proxyEcho(Map<String, Object> body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Request body is required"))
                    .build();
        }

        // Get the current authenticated user to determine knox_id
        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        String knoxId = ECHO_SHARED_KNOX_ID; // Default fallback
        if (currentUser != null && currentUser.getEmailAddress() != null) {
            String userEmail = currentUser.getEmailAddress();
            int atIndex = userEmail.indexOf("@");
            if (atIndex > 0) {
                knoxId = userEmail.substring(0, atIndex);
            }
        }

        String endpoint = (String) body.getOrDefault("endpoint", ECHO_DEFAULT_ENDPOINT);
        boolean stream = !body.containsKey("stream") || Boolean.TRUE.equals(body.get("stream"));
        String spaceKey = body.containsKey("spaceKey") ? (String) body.get("spaceKey") : null;
        // String prePrompt = body.containsKey("prePrompt") ? (String) body.get("prePrompt") : null;
        String query = body.containsKey("query") && body.get("query") != null
                ? body.get("query").toString()
                : "";

        String formattedQuery = endpoint.equals("feedback")
                ? ""
                : formatQueryWithSpace(query, spaceKey); //prePrompt

        Map<String, Object> requestPayload = new HashMap<>();
        if ("summary".equals(endpoint)) {
            requestPayload.put("page_id", formattedQuery);
            requestPayload.put("stream", stream);
        } else if ("feedback".equals(endpoint)) {
            requestPayload.put("thumbs_up", body.get("thumbs_up"));
            requestPayload.put("category", body.get("category"));
            requestPayload.put("comment", body.get("comment"));
            requestPayload.put("knox_id", knoxId);
            requestPayload.put("source", "confluence");
            requestPayload.put("echo_content", body.get("echo_content"));
        } else {
            requestPayload.put("query", formattedQuery);
            requestPayload.put("stream", stream);
            requestPayload.put("knox_id", knoxId);
        }

        String echoUrl = ECHO_BASE_URL + "/" + endpoint + "/";

        try {
            String accessToken = fetchEchoToken();
            HttpURLConnection connection = (HttpURLConnection) new URL(echoUrl).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + accessToken);
            connection.setConnectTimeout(ECHO_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ECHO_READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                String jsonBody = toJson(requestPayload);
                writer.write(jsonBody);
                writer.flush();
            }

            int status = connection.getResponseCode();
            String contentType = connection.getHeaderField("Content-Type");

            if (status != HttpURLConnection.HTTP_OK) {
                String errorText = readStream(connection.getErrorStream());
                Map<String, Object> error = new HashMap<>();
                error.put("error", true);
                error.put("status", status);
                error.put("message", "Echo service error");
                error.put("details", errorText);
                return Response.status(502).entity(error).build();
            }

            final InputStream echoStream = connection.getInputStream();

            if (stream && contentType != null && contentType.contains("text/event-stream")) {
                StreamingOutput streamingOutput = output -> {
                    try (InputStream in = echoStream) {
                        byte[] buffer = new byte[2048];
                        int read;
                        while ((read = in.read(buffer)) != -1) {
                            output.write(buffer, 0, read);
                            output.flush();
                        }
                    } catch (IOException ioEx) {
                        log.error("Echo proxy streaming error", ioEx);
                    }
                };

                return Response.ok(streamingOutput)
                        .header("Content-Type", "text/event-stream;charset=utf-8")
                        .header("Cache-Control", "no-cache")
                        .header("X-Accel-Buffering", "no")
                        .build();
            } else {
                String responseText = readStream(echoStream);
                return Response.ok(responseText)
                        .header("Content-Type", contentType != null ? contentType : MediaType.APPLICATION_JSON)
                        .build();
            }
        } catch (Exception ex) {
            log.error("Echo proxy error", ex);
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }

    /**
     * CloudGPT (Stargate) API health check
     * GET /rest/rail/1.0/cloudgpt
     */
    @GET
    @Path("cloudgpt")
    public Response cloudgptHealth() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("status", "ok");
        payload.put("endpoint", "cloudgpt");
        payload.put("apiUrl", CLOUDGPT_API_URL);
        payload.put("model", CLOUDGPT_MODEL);
        payload.put("timestamp", System.currentTimeMillis());
        return Response.ok(payload).build();
    }

    /**
     * Proxy CloudGPT (Stargate) API calls server-side to avoid browser CORS.
     * POST /rest/rail/1.0/cloudgpt
     *
     * Request body:
     * {
     *   "messages": [{"role": "user", "content": "..."}],
     *   "model": "general" (optional, defaults to "general")
     * }
     */
    @POST
    @Path("cloudgpt")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response proxyCloudGPT(Map<String, Object> body) {
        if (body == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Request body is required"))
                    .build();
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> messages = (List<Map<String, String>>) body.get("messages");
        if (messages == null || messages.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("messages array is required"))
                    .build();
        }

        String model = body.containsKey("model") ? (String) body.get("model") : CLOUDGPT_MODEL;

        try {
            // Build request payload
            Map<String, Object> requestPayload = new HashMap<>();
            requestPayload.put("model", model);
            requestPayload.put("messages", messages);

            HttpURLConnection connection = (HttpURLConnection) new URL(CLOUDGPT_API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + CLOUDGPT_BEARER_TOKEN);
            connection.setConnectTimeout(CLOUDGPT_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(CLOUDGPT_READ_TIMEOUT_MS);
            connection.setDoOutput(true);

            // Write request body - need to handle nested objects/arrays
            String jsonBody = toJsonWithArrays(requestPayload);
            try (OutputStream os = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(jsonBody);
                writer.flush();
            }

            int status = connection.getResponseCode();

            if (status != HttpURLConnection.HTTP_OK) {
                String errorText = readStream(connection.getErrorStream());
                log.error("CloudGPT API error: {} - {}", status, errorText);
                Map<String, Object> error = new HashMap<>();
                error.put("error", true);
                error.put("status", status);
                error.put("message", "CloudGPT service error");
                error.put("details", errorText);
                return Response.status(502).entity(error).build();
            }

            String responseText = readStream(connection.getInputStream());
            return Response.ok(responseText)
                    .header("Content-Type", MediaType.APPLICATION_JSON)
                    .build();

        } catch (Exception ex) {
            log.error("CloudGPT proxy error", ex);
            Map<String, Object> error = new HashMap<>();
            error.put("error", true);
            error.put("message", ex.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(error).build();
        }
    }

    // ==================== Helper Methods ====================

    private byte[] readLimited(InputStream inputStream, int maxBytes) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[8192];
        int total = 0;
        int nRead;
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            total += nRead;
            if (total > maxBytes) {
                throw new IllegalStateException("File exceeds maximum size of 5 MB");
            }
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Ensure the current user is a project admin for the provided project key.
     * Returns a Response when access should be denied, or null when access is allowed.
     */
    private Response enforceProjectAdmin(String projectKey) {
        ApplicationUser currentUser = authenticationContext.getLoggedInUser();
        if (currentUser == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                    .entity(createErrorResponse("Authentication required"))
                    .build();
        }

        ProjectManager projectManager = ComponentAccessor.getProjectManager();
        PermissionManager permissionManager = ComponentAccessor.getPermissionManager();

        if (projectManager == null || permissionManager == null) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(createErrorResponse("Permission services not available"))
                    .build();
        }

        Project project = projectManager.getProjectObjByKey(projectKey);
        if (project == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse("Project not found: " + projectKey))
                    .build();
        }

        if (!permissionManager.hasPermission(ProjectPermissions.ADMINISTER_PROJECTS, project, currentUser)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity(createErrorResponse("You must be a project administrator for project: " + projectKey))
                    .build();
        }

        return null;
    }

    /**
     * Create a standardized error response
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        error.put("timestamp", System.currentTimeMillis());
        return error;
    }

    private String fetchEchoToken() throws IOException {
        try {
            // Create a trust manager that accepts all certificates (for internal enterprise SSL)
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                    public void checkServerTrusted(X509Certificate[] certs, String authType) { }
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAllCerts, new SecureRandom());

            HttpsURLConnection connection = (HttpsURLConnection) new URL(ECHO_TOKEN_URL).openConnection();
            connection.setSSLSocketFactory(sslContext.getSocketFactory());
            connection.setHostnameVerifier((hostname, session) -> true); // Accept all hostnames
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setConnectTimeout(ECHO_CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ECHO_CONNECT_TIMEOUT_MS);
            connection.setDoOutput(true);

            String form = "grant_type=client_credentials"
                    + "&client_id=" + ECHO_CLIENT_ID
                    + "&client_secret=" + ECHO_CLIENT_SECRET;

            try (OutputStream os = connection.getOutputStream();
                 BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                writer.write(form);
                writer.flush();
            }

            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                String error = readStream(connection.getErrorStream());
                throw new IOException("Token request failed: " + status + " - " + error);
            }

            String response = readStream(connection.getInputStream());
            String token = extractTokenFromJson(response);
            if (token == null || token.isEmpty()) {
                throw new IOException("No access_token in OAuth response");
            }
            return token;
        } catch (Exception e) {
            throw new IOException("Failed to fetch Echo token: " + e.getMessage(), e);
        }
    }

    private String extractTokenFromJson(String json) {
        if (json == null) {
            return null;
        }
        int idx = json.indexOf("\"access_token\"");
        if (idx == -1) return null;
        int colon = json.indexOf(':', idx);
        if (colon == -1) return null;
        int quoteStart = json.indexOf('"', colon + 1);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    private String formatQueryWithSpace(String query, String spaceKey) { //, String prePrompt
        StringBuilder sb = new StringBuilder();
        if (spaceKey != null && !spaceKey.isEmpty()) {
            sb.append("#").append(spaceKey.trim()).append(" ");
        }
        // if (prePrompt != null && !prePrompt.trim().isEmpty()) {
        //     sb.append(prePrompt.trim()).append(" ");
        // }
        if (query != null) {
            sb.append(query.trim());
        }
        return sb.toString().trim();
    }

    private String toJson(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof Boolean || value instanceof Number) {
                sb.append(value.toString());
            } else {
                sb.append("\"").append(value.toString().replace("\"", "\\\"")).append("\"");
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * Serialize a Map to JSON, handling nested Lists and Maps (for CloudGPT messages array)
     */
    @SuppressWarnings("unchecked")
    private String toJsonWithArrays(Map<String, Object> data) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            if (!first) {
                sb.append(",");
            }
            sb.append("\"").append(escapeJsonString(entry.getKey())).append("\":");
            sb.append(toJsonValue(entry.getValue()));
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String toJsonValue(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean || value instanceof Number) {
            return value.toString();
        }
        if (value instanceof String) {
            return "\"" + escapeJsonString((String) value) + "\"";
        }
        if (value instanceof List) {
            StringBuilder sb = new StringBuilder();
            sb.append("[");
            boolean first = true;
            for (Object item : (List<?>) value) {
                if (!first) {
                    sb.append(",");
                }
                sb.append(toJsonValue(item));
                first = false;
            }
            sb.append("]");
            return sb.toString();
        }
        if (value instanceof Map) {
            return toJsonWithArrays((Map<String, Object>) value);
        }
        // Fallback for other types
        return "\"" + escapeJsonString(value.toString()) + "\"";
    }

    private String escapeJsonString(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String readStream(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    private ApplicationProperties resolveApplicationProperties() {
        ApplicationProperties properties = ComponentAccessor.getOSGiComponentInstanceOfType(ApplicationProperties.class);
        if (properties == null) {
            throw new IllegalStateException("ApplicationProperties service is not available");
        }
        return properties;
    }

    private static final String SAMPLE_PROJECT_KEY = "IKEJSM";
    private static final String SAMPLE_PORTAL_TITLE = "Customer Experience Portal";

    private PortalConfigDTO buildSamplePortalConfig(String projectKey, String portalId) {
        String resolvedProjectKey = (projectKey == null || projectKey.trim().isEmpty())
                ? SAMPLE_PROJECT_KEY
                : projectKey.toUpperCase(Locale.ENGLISH);
        String resolvedPortalId = (portalId == null || portalId.trim().isEmpty())
                ? resolvedProjectKey.toLowerCase(Locale.ENGLISH) + "-portal"
                : portalId.toLowerCase(Locale.ENGLISH);

        PortalConfigDTO dto = new PortalConfigDTO();
        dto.setProjectKey(resolvedProjectKey);
        ProjectDTO project = projectService.getProjectByKey(resolvedProjectKey);
        String derivedProjectName = project != null && project.getName() != null
                ? project.getName()
                : resolvedProjectKey + " Portal";
        dto.setProjectName(derivedProjectName);
        dto.setPortalId(resolvedPortalId);
        dto.setPortalTitle(SAMPLE_PORTAL_TITLE);
        dto.setComponents(buildSamplePortalComponents(resolvedProjectKey, derivedProjectName));
        dto.setRequestTypeGroups(buildSampleRequestTypeGroups());
        dto.setLive(false);
        dto.setUpdatedAt(System.currentTimeMillis());
        return dto;
    }

    private List<Map<String, Object>> buildSamplePortalComponents(String projectKey, String projectName) {
        List<Map<String, Object>> components = new ArrayList<>();

        Map<String, Object> hero = new HashMap<>();
        hero.put("id", "hero-banner-ike");
        hero.put("type", "hero-banner");
        hero.put("category", "content");
        hero.put("icon", "Image");
        hero.put("label", "Hero Banner");
        hero.put("label_info", "Hero section");
        hero.put("content", projectName);
        hero.put("description", "Submit incidents, access services, and stay informed about the latest releases.");
        Map<String, Object> heroProps = new HashMap<>();
        heroProps.put("height", "medium");
        // Default to Grid Pattern shader background
        heroProps.put("backgroundPreset", "shader");
        heroProps.put("selectedShader", "grid-pattern");
        heroProps.put("backgroundGradient", "from-primary/10 to-primary/0");
        // Default text colors
        heroProps.put("textColor", "foreground");
        // Show search bar by default
        heroProps.put("showSearchBar", "yes");
        // No button by default
        heroProps.put("showCTA", "no");
        heroProps.put("ctaText", "Open Portal");
        heroProps.put("ctaVariant", "default");
        heroProps.put("secondaryCTA", "View Guides");
        Map<String, Object> heroStyle = new HashMap<>();
        heroStyle.put("textAlign", "left");
        heroStyle.put("colSpan", "6");
        heroProps.put("style", heroStyle);
        hero.put("properties", heroProps);
        components.add(hero);

        Map<String, Object> requestTypes = new HashMap<>();
        requestTypes.put("id", "request-types-ike");
        requestTypes.put("type", "request-types");
        requestTypes.put("category", "content");
        requestTypes.put("icon", "LayoutGrid");
        requestTypes.put("label", "Request Types");
        requestTypes.put("label_info", resolvedLabelInfo(projectKey));
        requestTypes.put("content", "Popular Request Types");
        Map<String, Object> requestProps = new HashMap<>();
        requestProps.put("layout", "grid");
        requestProps.put("columns", "2");
        List<Map<String, Object>> reqTypesList = new ArrayList<>();
        reqTypesList.add(buildRequestType("ike-help", "Get Help", "General assistance from the support squad", "HelpCircle", "blue", "Support"));
        reqTypesList.add(buildRequestType("ike-incident", "Report Incident", "Flag production-impacting issues", "Bug", "red", "Support"));
        reqTypesList.add(buildRequestType("ike-access", "Access Request", "Provision or update system access", "ShieldCheck", "purple", "Services"));
        reqTypesList.add(buildRequestType("ike-change", "Request Change", "Submit improvements or configuration updates", "Lightbulb", "yellow", "Services"));
        requestProps.put("requestTypes", reqTypesList);
        requestTypes.put("properties", requestProps);
        components.add(requestTypes);

        Map<String, Object> jqlTable = new HashMap<>();
        jqlTable.put("id", "jql-table-ike");
        jqlTable.put("type", "jql-table");
        jqlTable.put("category", "content");
        jqlTable.put("icon", "Table");
        jqlTable.put("label", "JQL Table");
        jqlTable.put("label_info", resolvedLabelInfo(projectKey));
        jqlTable.put("content", "Latest " + projectKey + " Requests");
        Map<String, Object> tableProps = new HashMap<>();
        tableProps.put("jqlQuery", "project = " + projectKey + " ORDER BY created DESC");
        tableProps.put("columns", Arrays.asList("key", "summary", "status", "priority", "created"));
        tableProps.put("showSearch", "yes");
        tableProps.put("showFilter", "yes");
        tableProps.put("pageSize", "10");
        jqlTable.put("properties", tableProps);
        components.add(jqlTable);

        return components;
    }

    private Map<String, Object> buildRequestType(String id, String name, String description, String icon, String color, String group) {
        Map<String, Object> requestType = new HashMap<>();
        requestType.put("id", id);
        requestType.put("name", name);
        requestType.put("description", description);
        requestType.put("icon", icon);
        requestType.put("color", color);
        requestType.put("group", group);
        return requestType;
    }

    private String resolvedLabelInfo(String projectKey) {
        return projectKey + " request types";
    }

    private List<Map<String, Object>> buildSampleRequestTypeGroups() {
        List<Map<String, Object>> groups = new ArrayList<>();

        Map<String, Object> support = new HashMap<>();
        support.put("id", "support");
        support.put("name", "Support");
        support.put("types", Arrays.asList("ike-help", "ike-incident"));
        groups.add(support);

        Map<String, Object> services = new HashMap<>();
        services.put("id", "services");
        services.put("name", "Services");
        services.put("types", Arrays.asList("ike-access", "ike-change"));
        groups.add(services);

        return groups;
    }

    /**
     * Convert PortalConfigDTO to Map to bypass JAX-RS Jackson serialization issues.
     *
     * JAX-RS uses a different Jackson ObjectMapper configuration than PortalConfigService,
     * which causes all fields except 'live' to be stripped from HTTP responses.
     *
     * This workaround manually converts the DTO to a Map, which Jackson serializes correctly.
     *
     * @param dto The PortalConfigDTO to convert
     * @return Map representation of the DTO
     */
    private Map<String, Object> convertDtoToMap(PortalConfigDTO dto) {
        Map<String, Object> map = new HashMap<>();
        map.put("projectKey", dto.getProjectKey());
        map.put("projectName", dto.getProjectName());
        map.put("portalId", dto.getPortalId());
        map.put("portalTitle", dto.getPortalTitle());
        map.put("live", dto.isLive());
        map.put("components", dto.getComponents());
        map.put("requestTypeGroups", dto.getRequestTypeGroups());
        map.put("updatedAt", dto.getUpdatedAt());
        map.put("serviceDeskId", dto.getServiceDeskId());

        // Include isServiceDesk flag based on project type
        boolean isServiceDesk = false;
        if (dto.getProjectKey() != null) {
            isServiceDesk = projectService.isServiceDesk(dto.getProjectKey());
        }
        map.put("isServiceDesk", isServiceDesk);

        return map;
    }

    /**
     * Convert IssueSearchResponseDTO to Map to bypass JAX-RS Jackson serialization issues.
     * Mirrors the PortalConfigDTO workaround so the frontend always receives a stable JSON shape.
     */
    private Map<String, Object> convertIssueSearchResponseToMap(IssueSearchResponseDTO dto) {
        Map<String, Object> map = new HashMap<>();

        List<Map<String, Object>> issueMaps = dto.getIssues() != null
                ? dto.getIssues().stream()
                        .map(this::convertIssueDtoToMap)
                        .collect(Collectors.toList())
                : Collections.emptyList();

        map.put("issues", issueMaps);
        map.put("totalCount", dto.getTotalCount());
        map.put("startIndex", dto.getStartIndex());
        map.put("pageSize", dto.getPageSize());
        map.put("hasNextPage", dto.isHasNextPage());
        map.put("hasPreviousPage", dto.isHasPreviousPage());
        map.put("jqlQuery", dto.getJqlQuery());
        map.put("projectKey", dto.getProjectKey());
        map.put("executionTimeMs", dto.getExecutionTimeMs());
        map.put("currentPage", dto.getCurrentPage());
        map.put("totalPages", dto.getTotalPages());

        // User context for debugging permission issues
        map.put("searchedAsUserKey", dto.getSearchedAsUserKey());
        map.put("searchedAsUserName", dto.getSearchedAsUserName());
        map.put("searchedAsUserDisplayName", dto.getSearchedAsUserDisplayName());
        map.put("resolvedJqlQuery", dto.getResolvedJqlQuery());
        map.put("facets", dto.getFacets());
        return map;
    }

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
        map.put("serviceDeskId", issue.getServiceDeskId());
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
}
