// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/rest/AnnouncementBannerResource.java

package com.samsungbuilder.jsm.rest;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.crowd.embedded.api.GroupManager;
import com.samsungbuilder.jsm.dto.AnnouncementBannerConfigDTO;
import com.samsungbuilder.jsm.dto.ErrorEnvelopeDTO;
import com.samsungbuilder.jsm.service.AnnouncementBannerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Named
@Path("/announcement-banner")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AnnouncementBannerResource {
    private static final Logger log = LoggerFactory.getLogger(AnnouncementBannerResource.class);
    private static final String ADMIN_GROUP = "jira-administrators";

    private final AnnouncementBannerService announcementBannerService;
    private final JiraAuthenticationContext jiraAuthenticationContext;
    private final GroupManager groupManager;

    @Inject
    public AnnouncementBannerResource(
        AnnouncementBannerService announcementBannerService
    ) {
        this.announcementBannerService = announcementBannerService;
        this.jiraAuthenticationContext = ComponentAccessor.getJiraAuthenticationContext();
        this.groupManager = ComponentAccessor.getGroupManager();
    }

    @GET
    public Response getPublicConfig() {
        ApplicationUser user = currentUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorEnvelopeDTO("UNAUTHORIZED", "Authentication required"))
                .build();
        }

        AnnouncementBannerConfigDTO config = announcementBannerService.getConfig();
        return Response.ok(config).build();
    }

    @GET
    @Path("/admin")
    public Response getAdminConfig() {
        ApplicationUser user = currentUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorEnvelopeDTO("UNAUTHORIZED", "Authentication required"))
                .build();
        }
        if (!isRailAdmin(user)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorEnvelopeDTO("FORBIDDEN", "jira-administrators membership required"))
                .build();
        }

        return Response.ok(announcementBannerService.getConfig()).build();
    }

    @PUT
    @Path("/admin")
    public Response updateAdminConfig(AnnouncementBannerConfigDTO request) {
        ApplicationUser user = currentUser();
        if (user == null) {
            return Response.status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorEnvelopeDTO("UNAUTHORIZED", "Authentication required"))
                .build();
        }
        if (!isRailAdmin(user)) {
            return Response.status(Response.Status.FORBIDDEN)
                .entity(new ErrorEnvelopeDTO("FORBIDDEN", "jira-administrators membership required"))
                .build();
        }

        try {
            AnnouncementBannerConfigDTO saved =
                announcementBannerService.saveConfig(request, user.getName());
            return Response.ok(saved).build();
        } catch (Exception e) {
            log.error("Failed to update announcement banner", e);
            return Response.serverError()
                .entity(new ErrorEnvelopeDTO("SAVE_FAILED", "Unable to save announcement banner configuration"))
                .build();
        }
    }

    private ApplicationUser currentUser() {
        return jiraAuthenticationContext != null ? jiraAuthenticationContext.getLoggedInUser() : null;
    }

    private boolean isRailAdmin(ApplicationUser user) {
        return user != null && groupManager != null && groupManager.isUserInGroup(user, ADMIN_GROUP);
    }
}
