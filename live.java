/**
     * Toggle portal live flag.
     */
    @POST
    @Path("portals/project/{projectKey}/live")
    public Response setPortalLive(
            @PathParam("projectKey") String projectKey,
            Map<String, Object> body
    ) {
        boolean live = body != null && Boolean.TRUE.equals(body.get("live"));

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            PortalConfigDTO updated = portalConfigService.updateLiveState(projectKey, live);
            // FIX: Convert to Map to bypass Jackson serialization issues
            return Response.ok(convertDtoToMap(updated)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(ex.getMessage()))
                    .build();
        }
    }


public PortalConfigDTO updateLiveState(String projectKey, boolean live) {
        Optional<PortalConfigDTO> maybeConfig = getPortalConfig(projectKey);

        if (!maybeConfig.isPresent()) {
            throw new IllegalStateException(
                "Cannot update live state for project " + projectKey +
                " because no portal configuration exists. Please save a configuration first."
            );
        }

        PortalConfigDTO existing = maybeConfig.get();
        existing.setLive(live);
        existing.setUpdatedAt(System.currentTimeMillis());
        return savePortalConfig(projectKey, existing);
    }


public Optional<PortalConfigDTO> getPortalConfig(String projectKey) {
        Project project = resolveProject(projectKey);
        if (project == null) {
            log.warn("Project not found: {}", projectKey);
            return Optional.empty();
        }

        try {
            PluginSettings settings = pluginSettingsFactory.createGlobalSettings();
            String settingsKey = PROPERTY_KEY_PREFIX + projectKey.toUpperCase();
            Object rawValue = settings.get(settingsKey);

            if (rawValue == null) {
            log.debug("No portal config found for project {}", projectKey);
            return loadLegacyPortalConfig(project);
        }

            String jsonValue = rawValue.toString();
            PortalConfigDTO dto = objectMapper.readValue(jsonValue, PortalConfigDTO.class);
            hydratePortalDefaults(dto, project);
            
            log.debug("Successfully loaded portal config for project {} (size: {} bytes)", 
                     projectKey, jsonValue.length());
            
            return Optional.of(dto);
        } catch (IOException e) {
            log.error("Failed to parse portal config for {}", projectKey, e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get portal config for {}", projectKey, e);
            return Optional.empty();
        }
    }



package com.samsungbuilder.jsm.dto;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@XmlRootElement
@JsonIgnoreProperties(ignoreUnknown = true)
// REMOVED: @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
// This annotation was stripping fields from HTTP responses - history endpoint doesn't use it and works fine
public class PortalConfigDTO {

    private String projectKey;
    private String projectName;
    private String portalTitle;
    private String portalId;
    private String serviceDeskId;
    private boolean live;
    private List<Map<String, Object>> components;
    private List<Map<String, Object>> requestTypeGroups;
    private long updatedAt;

    public PortalConfigDTO() {
        // Initialize collections to empty lists to prevent NON_NULL serialization from stripping them
        // This matches the pattern used in PortalHistoryDTO
        this.components = new ArrayList<>();
        this.requestTypeGroups = new ArrayList<>();
    }

    public String getProjectKey() {
        return projectKey;
    }

    public void setProjectKey(String projectKey) {
        this.projectKey = projectKey;
    }

    public String getPortalTitle() {
        return portalTitle;
    }

    public void setPortalTitle(String portalTitle) {
        this.portalTitle = portalTitle;
    }

    public String getPortalId() {
        return portalId;
    }

    public void setPortalId(String portalId) {
        this.portalId = portalId;
    }

    public String getServiceDeskId() {
        return serviceDeskId;
    }

    public void setServiceDeskId(String serviceDeskId) {
        this.serviceDeskId = serviceDeskId;
    }

    public boolean isLive() {
        return live;
    }

    public void setLive(boolean live) {
        this.live = live;
    }

    public List<Map<String, Object>> getComponents() {
        if (components == null) {
            components = new ArrayList<>();
        }
        return components;
    }

    public void setComponents(List<Map<String, Object>> components) {
        this.components = components;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public List<Map<String, Object>> getRequestTypeGroups() {
        if (requestTypeGroups == null) {
            requestTypeGroups = new ArrayList<>();
        }
        return requestTypeGroups;
    }

    public void setRequestTypeGroups(List<Map<String, Object>> requestTypeGroups) {
        this.requestTypeGroups = requestTypeGroups;
    }
}

// ==================== Portal Config Endpoints ====================

    /**
     * Fetch portal configuration for a project key.
     *
     * FIX: Returns Map instead of DTO to bypass JAX-RS Jackson serialization issues.
     * JAX-RS uses a different Jackson ObjectMapper than PortalConfigService, which
     * strips all fields except 'live' from the response. Converting to Map works around this.
     */
    @GET
    @Path("portals/project/{projectKey}")
    public Response getPortalConfigForProject(@PathParam("projectKey") String projectKey) {
        log.info("GET /portals/project/{} - Fetching portal configuration", projectKey);

        Optional<PortalConfigDTO> config = portalConfigService.getPortalConfig(projectKey);

        if (config.isPresent()) {
            PortalConfigDTO dto = config.get();
            log.info("  Config exists - hasComponents: {}, componentsSize: {}",
                     dto.getComponents() != null,
                     dto.getComponents() != null ? dto.getComponents().size() : 0);

            // DIAGNOSTIC: Log the DTO before serialization
            log.info("  DTO before serialization - projectKey: {}, portalId: {}, live: {}, componentsSize: {}",
                     dto.getProjectKey(), dto.getPortalId(), dto.isLive(),
                     dto.getComponents() != null ? dto.getComponents().size() : "null");

            // FIX: Convert DTO to Map to bypass Jackson serialization issues
            Map<String, Object> configMap = convertDtoToMap(dto);

            log.info("  Returning Map with {} components",
                     ((List<?>) configMap.get("components")).size());

            return Response.ok(configMap).build();
        }

        log.info("  No config found - creating sample config");
        PortalConfigDTO sample = buildSamplePortalConfig(projectKey, null);
        portalConfigService.savePortalConfig(projectKey, sample);
        log.info("  Sample config created and saved with {} components", sample.getComponents().size());

        // FIX: Convert sample to Map as well
        Map<String, Object> sampleMap = convertDtoToMap(sample);
        return Response.ok(sampleMap).build();
    }

@POST
    @Path("portals/project/{projectKey}")
    public Response savePortalConfig(@PathParam("projectKey") String projectKey, PortalConfigDTO config) {
        if (config == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(createErrorResponse("Portal payload is required"))
                    .build();
        }

        Response denial = enforceProjectAdmin(projectKey);
        if (denial != null) {
            return denial;
        }

        try {
            log.info("POST /portals/project/{} - Saving portal config", projectKey);
            log.info("  Received components count: {}", config.getComponents() != null ? config.getComponents().size() : 0);

            PortalConfigDTO saved = portalConfigService.savePortalConfig(projectKey, config);

            log.info("  Saved successfully - components count: {}", saved.getComponents() != null ? saved.getComponents().size() : 0);
            log.info("  Returning saved config with projectKey: {}, portalId: {}", saved.getProjectKey(), saved.getPortalId());

            // FIX: Convert to Map to bypass Jackson serialization issues
            return Response.ok(convertDtoToMap(saved)).build();
        } catch (IllegalArgumentException ex) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(createErrorResponse(ex.getMessage()))
                    .build();
        }
    }
