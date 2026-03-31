// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/servlet/CustomerRailServlet.java

package com.samsungbuilder.jsm.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.JiraAuthenticationContext;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.ApplicationProperties;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.ServiceDeskManager;
import com.samsungbuilder.jsm.dto.PortalConfigDTO;
import com.samsungbuilder.jsm.service.PortalConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Customer Rail Servlet - Customer-facing portal interface
 *
 * This servlet provides the customer-facing portal interface for RAIL Portal.
 * It handles project-specific portal views with projectKey as a path parameter.
 *
 * Canonical URL: /plugins/servlet/customer-rail/{projectKey}
 *
 * This servlet bootstraps the React app with the customer portal view for the specified project.
 *
 * IMPORTANT: If the portal is NOT Live (isLive=false), this servlet redirects users
 * back to the OOTB JSM portal instead of rendering the custom portal.
 */
public class CustomerRailServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CustomerRailServlet.class);
    private static final String PLUGIN_KEY = "com.samsungbuilder.jsm.rail-portal";
    private static final String RESOURCE_KEY = "com.samsungbuilder.jsm.rail-portal:rail-portal-resources";
    private static final String MOUNT_NODE_ID = "customer-rail-root";
    private static final String CSS_RESOURCE_NAME = "rail-portal.css";
    private static final String JS_RESOURCE_NAME = "rail-portal.js";

    // Lazily resolved OSGi services
    private PortalConfigService portalConfigService;
    private ServiceDeskManager serviceDeskManager;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");

        // AUTHENTICATION CHECK: Ensure user is logged in before allowing access
        // The <customercontext> module allows unlicensed JSM customers, but we still require authentication
        JiraAuthenticationContext authContext = ComponentAccessor.getJiraAuthenticationContext();
        ApplicationUser currentUser = authContext != null ? authContext.getLoggedInUser() : null;

        if (currentUser == null) {
            // User is not authenticated - redirect to SSO login with return URL
            String returnUrl = req.getRequestURI();
            String queryString = req.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                returnUrl += "?" + queryString;
            }
            String contextPath = normalizeContextPath(req.getContextPath());
            String loginUrl = contextPath + "/login.jsp?os_destination=" +
                              URLEncoder.encode(returnUrl, StandardCharsets.UTF_8.name());
            log.info("CustomerRailServlet - Unauthenticated access attempt, redirecting to login: {}", returnUrl);
            resp.sendRedirect(loginUrl);
            return;
        }

        ApplicationProperties applicationProperties = ComponentAccessor.getOSGiComponentInstanceOfType(ApplicationProperties.class);
        if (applicationProperties == null) {
            throw new IllegalStateException("ApplicationProperties service is not available");
        }

        String contextPath = normalizeContextPath(req.getContextPath());
        String baseUrl = determineBaseUrl(applicationProperties, req, contextPath);
        String pathInfo = req.getPathInfo();

        // Extract projectKey from path
        String projectKey = extractProjectKeyFromPath(pathInfo);

        // If no projectKey in path, render landing page
        if (projectKey == null || projectKey.trim().isEmpty()) {
            renderCustomerPortal(resp, contextPath, baseUrl, null);
            return;
        }

        // Validate project exists
        ProjectManager projectManager = ComponentAccessor.getProjectManager();
        Project project = projectManager != null ? projectManager.getProjectObjByKey(projectKey.trim()) : null;

        if (project == null) {
            renderError(resp, contextPath, "Project Not Found",
                       "Project '" + projectKey + "' not found. Please verify the project key.");
            return;
        }

        // CHECK IF PORTAL IS LIVE - if not, redirect to OOTB JSM portal
        if (!isPortalLive(projectKey)) {
            log.info("CustomerRailServlet - Portal for {} is NOT Live, redirecting to OOTB JSM portal", projectKey);
            String ootbPortalUrl = buildOotbPortalUrl(req, contextPath, projectKey);
            if (ootbPortalUrl != null) {
                resp.sendRedirect(ootbPortalUrl);
                return;
            }
            // If we can't determine the OOTB URL, show an error
            renderError(resp, contextPath, "Portal Not Available",
                       "The custom portal for '" + projectKey + "' is not currently active.");
            return;
        }

        // Render customer portal for the project
        renderCustomerPortal(resp, contextPath, baseUrl, project);
    }

    /**
     * Check if the portal is marked as Live in the portal configuration.
     *
     * LOGIC:
     * - If we can get config and isLive=true → return true (render portal)
     * - If we can get config and isLive=false → return false (redirect to OOTB)
     * - If no config exists → return false (portal not set up, redirect to OOTB)
     * - If service unavailable → return false (safer to redirect to OOTB)
     *
     * The skipRail=true parameter prevents redirect loops when redirecting to OOTB.
     */
    private boolean isPortalLive(String projectKey) {
        try {
            System.out.println(">>> CustomerRailServlet - isPortalLive check for: " + projectKey);

            // Try to resolve services - will use OSGi export now
            resolveServices();

            if (portalConfigService == null) {
                // Service STILL unavailable after resolve attempt
                System.out.println(">>> CustomerRailServlet - PortalConfigService NULL after resolve, returning FALSE");
                log.warn("CustomerRailServlet - PortalConfigService unavailable for {}, redirecting to OOTB", projectKey);
                return false;
            }

            System.out.println(">>> CustomerRailServlet - PortalConfigService resolved OK, checking config for: " + projectKey);
            Optional<PortalConfigDTO> configOpt = portalConfigService.getPortalConfig(projectKey);

            if (configOpt.isPresent()) {
                boolean isLive = configOpt.get().isLive();
                System.out.println(">>> CustomerRailServlet - Portal " + projectKey + " isLive=" + isLive);
                return isLive;
            } else {
                // No config exists - portal not set up yet, redirect to OOTB
                System.out.println(">>> CustomerRailServlet - No config for " + projectKey + ", returning FALSE");
                return false;
            }
        } catch (Exception e) {
            // Error during check - redirect to OOTB for safety
            System.out.println(">>> CustomerRailServlet - Error: " + e.getMessage() + ", returning FALSE");
            log.error("CustomerRailServlet - Error checking Live status for {}: {}", projectKey, e.getMessage());
            return false;
        }
    }

    /**
     * Build the OOTB JSM portal URL for redirect when portal is not Live.
     * Uses query parameter to prevent the filter from intercepting.
     */
    private String buildOotbPortalUrl(HttpServletRequest req, String contextPath, String projectKey) {
        try {
            resolveServices();
            if (serviceDeskManager == null) {
                System.out.println(">>> CustomerRailServlet - ServiceDeskManager NULL for OOTB redirect");
                return null;
            }

            // Get the portal ID for this project
            ProjectManager projectManager = ComponentAccessor.getProjectManager();
            Project project = projectManager != null ? projectManager.getProjectObjByKey(projectKey) : null;
            if (project == null) {
                return null;
            }

            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskForProject(project);
            if (serviceDesk == null) {
                System.out.println(">>> CustomerRailServlet - No ServiceDesk for project " + projectKey);
                return null;
            }

            int portalId = serviceDesk.getId();
            // Add skipRail=true query parameter so the filter knows not to redirect this
            String ootbUrl = contextPath + "/servicedesk/customer/portal/" + portalId + "/?skipRail=true";
            System.out.println(">>> CustomerRailServlet - Redirecting to OOTB: " + ootbUrl);
            return ootbUrl;
        } catch (Exception e) {
            System.out.println(">>> CustomerRailServlet - Error building OOTB URL: " + e.getMessage());
            return null;
        }
    }

    /**
     * Resolve OSGi services lazily.
     */
    private void resolveServices() {
        if (portalConfigService == null) {
            portalConfigService = ComponentAccessor.getOSGiComponentInstanceOfType(PortalConfigService.class);
        }
        if (serviceDeskManager == null) {
            serviceDeskManager = ComponentAccessor.getOSGiComponentInstanceOfType(ServiceDeskManager.class);
        }
    }

    private void renderCustomerPortal(HttpServletResponse resp, String contextPath, String baseUrl, Project project) throws IOException {
        // If project is null, render landing page
        String projectKey = project != null ? project.getKey() : "";
        String projectName = project != null ? project.getName() : "RAIL Portal";
        String portalId = project != null ? project.getKey().toLowerCase(Locale.ENGLISH) + "-portal" : "landing";

        Map<String, Object> context = new HashMap<>();
        context.put("baseUrl", baseUrl);
        context.put("resourceKey", RESOURCE_KEY);
        context.put("mountNodeId", MOUNT_NODE_ID);
        context.put("resourceBase", buildResourceBase(contextPath));
        context.put("cssResourceName", CSS_RESOURCE_NAME);
        context.put("jsResourceName", JS_RESOURCE_NAME);
        context.put("initialView", "customer-portal");
        context.put("projectKey", projectKey);
        context.put("projectName", projectName);
        context.put("portalId", portalId);

        renderInlineHTML(resp, context);
    }

    private String extractProjectKeyFromPath(String pathInfo) {
        if (pathInfo == null || pathInfo.trim().isEmpty() || "/".equals(pathInfo.trim())) {
            return null;
        }

        String[] segments = pathInfo.split("/");
        for (String segment : segments) {
            if (segment != null && !segment.trim().isEmpty()) {
                return segment.trim().toUpperCase(Locale.ENGLISH);
            }
        }
        return null;
    }

    private void renderError(HttpServletResponse resp, String contextPath, String title, String message) throws IOException {
        String resourceBase = contextPath + "/download/resources/" + RESOURCE_KEY;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <title>").append(escapeHtml(title)).append(" - RAIL Portal</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"").append(resourceBase).append("/").append(CSS_RESOURCE_NAME).append("\" />\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div style=\"max-width: 600px; margin: 3rem auto; padding: 2rem; background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); text-align: center;\">\n");
        html.append("        <h1 style=\"color: #172b4d;\">").append(escapeHtml(title)).append("</h1>\n");
        html.append("        <p style=\"color: #5e6c84;\">").append(escapeHtml(message)).append("</p>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        resp.getWriter().write(html.toString());
    }

    private void renderInlineHTML(HttpServletResponse resp, Map<String, Object> context) throws IOException {
        String baseUrl = (String) context.get("baseUrl");
        String resourceBase = (String) context.get("resourceBase");
        String resourceKey = (String) context.get("resourceKey");
        String mountNodeId = (String) context.get("mountNodeId");
        String cssResourceName = (String) context.get("cssResourceName");
        String jsResourceName = (String) context.get("jsResourceName");
        String initialView = (String) context.get("initialView");
        String projectKey = (String) context.getOrDefault("projectKey", "");
        String projectName = (String) context.getOrDefault("projectName", "RAIL Portal");
        String portalId = (String) context.getOrDefault("portalId", "");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta name=\"decorator\" content=\"none\" />\n");
        html.append("    <meta name=\"application-base-url\" content=\"").append(baseUrl).append("\" />\n");
        html.append("    <meta charset=\"UTF-8\">\n");
        html.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n");
        String pageTitle = projectKey.isEmpty() ? "RAIL Portal" : projectName + " - RAIL Portal";
        html.append("    <title>").append(escapeHtml(pageTitle)).append("</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"").append(resourceBase).append("/").append(cssResourceName).append("\" />\n");
        html.append("    <style>\n");
        html.append("        body { background: #ffffff; margin: 0; }\n");
        html.append("        .customer-portal-shell { min-height: 100vh; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"customer-portal-shell\">\n");
        html.append("        <div id=\"").append(mountNodeId).append("\" class=\"customer-portal-root\"></div>\n");
        html.append("    </div>\n");
        html.append("    <script src=\"").append(resourceBase).append("/").append(jsResourceName).append("\"></script>\n");
        html.append("    <script type=\"text/javascript\">\n");
        html.append("        (function () {\n");
        html.append("            var mountId = \"").append(mountNodeId).append("\";\n");
        html.append("            var mountNode = document.getElementById(mountId);\n");
        html.append("            if (!mountNode) {\n");
        html.append("                console.error(\"Customer Portal mount node not found\", mountId);\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            var context = {\n");
        html.append("                baseUrl: \"").append(baseUrl).append("\",\n");
        html.append("                resourceKey: \"").append(resourceKey).append("\",\n");
        html.append("                resourceBase: \"").append(resourceBase).append("\",\n");
        html.append("                mountNodeId: mountId,\n");
        html.append("                initialView: \"").append(initialView).append("\",\n");
        html.append("                projectKey: \"").append(escapeJs(projectKey)).append("\",\n");
        html.append("                projectName: \"").append(escapeJs(projectName)).append("\",\n");
        html.append("                portalId: \"").append(escapeJs(portalId)).append("\"\n");
        html.append("            };\n");
        html.append("            window.RAIL_PORTAL_BOOTSTRAP = context;\n");
        html.append("            if (typeof window.initializeRailPortalApp === \"function\") {\n");
        html.append("                window.initializeRailPortalApp(context);\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            var fallbackHtml = '<div class=\"aui-message aui-message-warning\">' +\n");
        html.append("                '<p>Customer Portal is loading...</p></div>';\n");
        html.append("            mountNode.innerHTML = fallbackHtml;\n");
        html.append("            console.warn(\"Customer Portal bundle expected web resource:\", \"").append(resourceKey).append("\");\n");
        html.append("        })();\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        resp.getWriter().write(html.toString());
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.isEmpty() || "/".equals(contextPath)) {
            return "";
        }
        return contextPath;
    }

    private String determineBaseUrl(ApplicationProperties props, HttpServletRequest req, String contextPath) {
        String baseUrl = props.getBaseUrl();
        if (baseUrl == null || baseUrl.isEmpty()) {
            String scheme = req.getScheme();
            String serverName = req.getServerName();
            int serverPort = req.getServerPort();
            StringBuilder url = new StringBuilder();
            url.append(scheme).append("://").append(serverName);
            if ((scheme.equals("http") && serverPort != 80) || (scheme.equals("https") && serverPort != 443)) {
                url.append(":").append(serverPort);
            }
            url.append(contextPath);
            baseUrl = url.toString();
        }
        return baseUrl;
    }

    private String buildResourceBase(String contextPath) {
        return contextPath + "/download/resources/" + RESOURCE_KEY;
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

    /**
     * Escape JavaScript string (for use in script tags)
     */
    private String escapeJs(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("'", "\\'")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r");
    }
}
