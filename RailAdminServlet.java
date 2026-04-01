// /rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/servlet/RailAdminServlet.java
package com.samsungbuilder.jsm.servlet;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.sal.api.ApplicationProperties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * RAIL Admin Configuration Servlet
 *
 * Handles admin configuration routes for global RAIL Portal settings.
 * Canonical URL: /plugins/servlet/rail/admin
 *
 * This servlet is only accessible by Jira administrators and bootstraps
 * the React app with the admin configuration view.
 */
public class RailAdminServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final String PLUGIN_KEY = "com.samsungbuilder.jsm.rail-portal";
    private static final String RESOURCE_KEY = "com.samsungbuilder.jsm.rail-portal:rail-portal-resources";
    private static final String MOUNT_NODE_ID = "rail-portal-root";
    private static final String CSS_RESOURCE_NAME = "rail-portal.css";
    private static final String JS_RESOURCE_NAME = "rail-portal.js";
    private static final String ADMIN_GROUP = "jira-administrators";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setContentType("text/html;charset=UTF-8");

        String contextPath = normalizeContextPath(req.getContextPath());

        // Check if user is authenticated and is an admin
        ApplicationUser currentUser = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        if (currentUser == null) {
            resp.sendRedirect(contextPath + "/login.jsp?os_destination=" +
                    java.net.URLEncoder.encode(req.getRequestURI(), "UTF-8"));
            return;
        }

        // Check admin group membership
        GroupManager groupManager = ComponentAccessor.getGroupManager();
        var adminGroup = groupManager.getGroup(ADMIN_GROUP);
        boolean isAdmin = adminGroup != null && groupManager.isUserInGroup(currentUser, adminGroup);

        if (!isAdmin) {
            renderError(resp, contextPath, "Access Denied",
                       "You must be a Jira administrator to access this page.");
            return;
        }

        // Get services from OSGi
        ApplicationProperties applicationProperties = ComponentAccessor.getOSGiComponentInstanceOfType(ApplicationProperties.class);
        if (applicationProperties == null) {
            throw new IllegalStateException("ApplicationProperties service is not available");
        }

        String baseUrl = determineBaseUrl(applicationProperties, req, contextPath);

        // Build bootstrap context for React app
        Map<String, Object> context = new HashMap<>();
        context.put("baseUrl", baseUrl);
        context.put("resourceKey", RESOURCE_KEY);
        context.put("mountNodeId", MOUNT_NODE_ID);
        context.put("resourceBase", buildResourceBase(contextPath));
        context.put("cssResourceName", CSS_RESOURCE_NAME);
        context.put("jsResourceName", JS_RESOURCE_NAME);
        context.put("initialView", "admin");
        context.put("contextPath", contextPath);
        context.put("currentUser", currentUser.getUsername());

        // Render inline HTML with React app bootstrap
        renderInlineHTML(resp, context);
    }

    /**
     * Render error page with user-friendly message
     */
    private void renderError(HttpServletResponse resp, String contextPath, String title, String message) throws IOException {
        String resourceBase = contextPath + "/download/resources/" + RESOURCE_KEY;

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta name=\"decorator\" content=\"none\" />\n");
        html.append("    <title>").append(title).append(" - RAIL Admin</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"").append(resourceBase).append("/").append(CSS_RESOURCE_NAME).append("\" />\n");
        html.append("    <style>\n");
        html.append("        body { background: #f4f5f7; margin: 0; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, Ubuntu, sans-serif; }\n");
        html.append("        .error-container { display: flex; align-items: center; justify-content: center; min-height: 100vh; padding: 2rem; }\n");
        html.append("        .error-card { background: white; border-radius: 8px; box-shadow: 0 2px 8px rgba(0,0,0,0.1); padding: 3rem; max-width: 500px; text-align: center; }\n");
        html.append("        .error-card h1 { color: #172b4d; font-size: 1.5rem; margin: 0 0 1rem; }\n");
        html.append("        .error-card p { color: #5e6c84; line-height: 1.6; margin: 0 0 2rem; }\n");
        html.append("        .error-card a { display: inline-block; background: #0052cc; color: white; padding: 0.75rem 1.5rem; border-radius: 4px; text-decoration: none; font-weight: 500; }\n");
        html.append("        .error-card a:hover { background: #0065ff; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"error-container\">\n");
        html.append("        <div class=\"error-card\">\n");
        html.append("            <h1>").append(escapeHtml(title)).append("</h1>\n");
        html.append("            <p>").append(escapeHtml(message)).append("</p>\n");
        html.append("            <a href=\"").append(contextPath).append("/secure/Dashboard.jspa\">Return to Dashboard</a>\n");
        html.append("        </div>\n");
        html.append("    </div>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        resp.getWriter().write(html.toString());
    }

    /**
     * Render inline HTML for React app bootstrap
     */
    private void renderInlineHTML(HttpServletResponse resp, Map<String, Object> context) throws IOException {
        String baseUrl = (String) context.get("baseUrl");
        String resourceBase = (String) context.get("resourceBase");
        String resourceKey = (String) context.get("resourceKey");
        String mountNodeId = (String) context.get("mountNodeId");
        String cssResourceName = (String) context.get("cssResourceName");
        String jsResourceName = (String) context.get("jsResourceName");
        String initialView = (String) context.get("initialView");
        String currentUser = (String) context.getOrDefault("currentUser", "");
        String contextPath = (String) context.getOrDefault("contextPath", "");

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n");
        html.append("<html>\n");
        html.append("<head>\n");
        html.append("    <meta name=\"decorator\" content=\"atl.admin\" />\n");
        html.append("    <meta name=\"application-base-url\" content=\"").append(baseUrl).append("\" />\n");
        html.append("    <title>RAIL Admin Configuration</title>\n");
        html.append("    <link rel=\"stylesheet\" href=\"").append(resourceBase).append("/").append(cssResourceName).append("\" />\n");
        html.append("    <style>\n");
        html.append("        * { box-sizing: border-box; }\n");
        html.append("        .rail-admin-shell { min-height: 100vh; }\n");
        html.append("    </style>\n");
        html.append("</head>\n");
        html.append("<body>\n");
        html.append("    <div class=\"rail-admin-shell\">\n");
        html.append("        <div id=\"").append(mountNodeId).append("\" class=\"rail-portal-root\"></div>\n");
        html.append("    </div>\n");
        html.append("    <script src=\"").append(resourceBase).append("/").append(jsResourceName).append("\"></script>\n");
        html.append("    <script type=\"text/javascript\">\n");
        html.append("        (function () {\n");
        html.append("            var mountId = \"").append(mountNodeId).append("\";\n");
        html.append("            var mountNode = document.getElementById(mountId);\n");
        html.append("            if (!mountNode) {\n");
        html.append("                console.error(\"RAIL Portal mount node not found\", mountId);\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            var context = {\n");
        html.append("                baseUrl: \"").append(baseUrl).append("\",\n");
        html.append("                resourceKey: \"").append(resourceKey).append("\",\n");
        html.append("                resourceBase: \"").append(resourceBase).append("\",\n");
        html.append("                mountNodeId: mountId,\n");
        html.append("                initialView: \"").append(initialView).append("\",\n");
        html.append("                currentUser: \"").append(escapeJs(currentUser)).append("\"\n");
        html.append("            };\n");
        html.append("            window.RAIL_PORTAL_BOOTSTRAP = context;\n");
        html.append("            if (typeof window.initializeRailPortalApp === \"function\") {\n");
        html.append("                window.initializeRailPortalApp(context);\n");
        html.append("                return;\n");
        html.append("            }\n");
        html.append("            var fallbackHtml = '<div class=\"aui-message aui-message-warning\">' +\n");
        html.append("                '<p>RAIL Portal bundle is loading...</p></div>';\n");
        html.append("            mountNode.innerHTML = fallbackHtml;\n");
        html.append("            console.warn(\"RAIL Portal bundle expected web resource:\", \"").append(resourceKey).append("\");\n");
        html.append("        })();\n");
        html.append("    </script>\n");
        html.append("</body>\n");
        html.append("</html>\n");

        resp.getWriter().write(html.toString());
    }

    private String determineBaseUrl(ApplicationProperties applicationProperties, HttpServletRequest req, String contextPath) {
        String configuredBaseUrl = trimTrailingSlash(applicationProperties.getBaseUrl());
        if (configuredBaseUrl != null && !configuredBaseUrl.trim().isEmpty()) {
            if (!contextPath.isEmpty() && !configuredBaseUrl.endsWith(contextPath)) {
                return trimTrailingSlash(configuredBaseUrl + contextPath);
            }
            return configuredBaseUrl;
        }

        String requestUrl = req.getRequestURL().toString();
        String requestUri = req.getRequestURI();
        String fallbackBaseUrl = requestUrl.substring(0, requestUrl.length() - requestUri.length());
        return trimTrailingSlash(fallbackBaseUrl + contextPath);
    }

    private String buildResourceBase(String contextPath) {
        return contextPath + "/download/resources/" + RESOURCE_KEY;
    }

    private String trimTrailingSlash(String url) {
        if (url == null || url.length() <= 1) {
            return url;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private String normalizeContextPath(String contextPath) {
        if (contextPath == null || contextPath.equals("/")) {
            return "";
        }
        return trimTrailingSlash(contextPath);
    }

    private String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }

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

