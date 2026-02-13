/* rail-at-sas/backend/src/main/java/com/samsungbuilder/jsm/filter/RailCustomerPortalRedirectFilter.java */
package com.samsungbuilder.jsm.filter;

import com.atlassian.jira.project.Project;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.plugin.spring.scanner.annotation.imports.ComponentImport;
import com.atlassian.servicedesk.api.ServiceDesk;
import com.atlassian.servicedesk.api.ServiceDeskManager;
import com.samsungbuilder.jsm.dto.PortalConfigDTO;
import com.samsungbuilder.jsm.service.PortalConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;


import javax.inject.Inject;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servlet filter that redirects legacy JSM portal URLs to RAIL Portal when the portal is Live.
 *
 * This filter intercepts requests to:
 * 1. Default JSM URL: /servicedesk/customer/portal/{portalID}
 * 2. Refined Plugin URL: /plugins/servlet/desk/portal/{portalID}/
 * 3. Old Custom Plugin URL: /plugins/servlet/desk/portal/{portalID}/rail/
 *
 * When a Live RAIL portal exists for the project, it redirects to:
 *   /plugins/servlet/customer-rail/{projectKey}
 *
 * If the portal is not Live or no RAIL config exists, the request passes through
 * to the original destination.
 *
 * Uses @Inject constructor-based dependency injection (Spring-managed filter).
 */
public class RailCustomerPortalRedirectFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RailCustomerPortalRedirectFilter.class);
    private static final String ALREADY_FILTERED = RailCustomerPortalRedirectFilter.class.getName() + "_already_filtered";

    // Target URL for RAIL Portal (project-specific)
private static final String RAIL_PORTAL_PATH = "/plugins/servlet/customer-rail/";

// Target URL for RAIL "home" (no project key) - ALWAYS redirect for certain legacy home URLs
private static final String RAIL_HOME_PATH = "/plugins/servlet/customer-rail";

/**
 * Exact legacy homepage routes that should ALWAYS redirect to RAIL_HOME_PATH
 * (no isLive check, no skipRail override).
 *
 * IMPORTANT: values should be normalized (no trailing slash).
 */
private static final Set<String> ALWAYS_REDIRECT_HOME_PATHS = Collections.unmodifiableSet(new HashSet<String>() {{
    add("/servicedesk/customer/portals");
    add("/plugins/servlet/desk");
    add("/plugins/servlet/desk/site/global");

    // Optional - only include if it's real in your environment:
    // add("/plugins/servlet/desk/site/gloval");
}});

private String normalizePath(String path) {
    if (path == null || path.isEmpty()) return path;
    if ("/".equals(path)) return path;

    // Remove ONE trailing slash for consistency
    if (path.endsWith("/")) {
        return path.substring(0, path.length() - 1);
    }
    return path;
}

private String appendQueryString(HttpServletRequest request, String target) {
    String qs = request.getQueryString();
    if (qs != null && !qs.isEmpty()) {
        return target + "?" + qs;
    }
    return target;
}

private boolean isAlwaysRedirectHomePath(String path) {
    String normalized = normalizePath(path);
    return normalized != null && ALWAYS_REDIRECT_HOME_PATHS.contains(normalized);
}


    // Pattern 1: Default Jira Service Management URL
    // Matches: /servicedesk/customer/portal/5 or /servicedesk/customer/portal/5/ or /servicedesk/customer/portal/5/anything
    // NOTE: Using find() not matches() - no ^ anchor needed
    private static final Pattern JSM_PORTAL_PATTERN = Pattern.compile(
        "/servicedesk/customer/portal/(\\d+)(/.*)?$"
    );

    // Pattern 2: Refined/Desk Plugin URL (base) - matches like deprecated filter
    // Matches: /plugins/servlet/desk/portal/5 or /plugins/servlet/desk/portal/5/
    private static final Pattern DESK_PORTAL_BASE_PATTERN = Pattern.compile(
        "/plugins/servlet/desk/portal/(\\d+)(/)?$"
    );

    // Pattern 3: Old Custom Plugin URL (with /rail/ suffix)
    // Matches: /plugins/servlet/desk/portal/5/rail or /plugins/servlet/desk/portal/5/rail/
    private static final Pattern DESK_PORTAL_RAIL_PATTERN = Pattern.compile(
        "/plugins/servlet/desk/portal/(\\d+)/rail(/)?$"
    );

    // Pattern 4: JSM Request Type URL - for redirecting to RAIL request type page
    // Matches: /servicedesk/customer/portal/5/create/123 or /servicedesk/customer/portal/5/create/123/
    private static final Pattern JSM_REQUEST_TYPE_PATTERN = Pattern.compile(
        "/servicedesk/customer/portal/(\\d+)/create/(\\d+)(/)?$"
    );

    // Pattern 5: Refined/Desk Request Type URL - for redirecting to RAIL request type page
    // Matches: /plugins/servlet/desk/portal/5/create/123 or /plugins/servlet/desk/portal/5/create/123/
    private static final Pattern DESK_REQUEST_TYPE_PATTERN = Pattern.compile(
        "/plugins/servlet/desk/portal/(\\d+)/create/(\\d+)(/)?$"
    );

    // Cache for portal ID to project key mappings to avoid repeated API calls
    private final ConcurrentMap<Integer, String> portalIdCache = new ConcurrentHashMap<>();

    // Injected dependencies (Spring-managed)
    private final PortalConfigService portalConfigService;
    private final ServiceDeskManager serviceDeskManager;
    private final ProjectManager projectManager;

    /**
     * Constructor with dependency injection - same pattern as deprecated filter.
     * Dependencies are injected by Spring at plugin startup.
     */
    @Inject
    public RailCustomerPortalRedirectFilter(
            PortalConfigService portalConfigService,
            @ComponentImport ServiceDeskManager serviceDeskManager,
            @ComponentImport ProjectManager projectManager) {
        this.portalConfigService = portalConfigService;
        this.serviceDeskManager = serviceDeskManager;
        this.projectManager = projectManager;

        // Log immediately at construction time
        System.out.println(">>> RAIL Filter - Constructor called!");
        System.out.println(">>> RAIL Filter - portalConfigService=" + (portalConfigService != null ? "OK" : "NULL"));
        System.out.println(">>> RAIL Filter - serviceDeskManager=" + (serviceDeskManager != null ? "OK" : "NULL"));
        System.out.println(">>> RAIL Filter - projectManager=" + (projectManager != null ? "OK" : "NULL"));
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // Use System.out for guaranteed visibility (SLF4J might be filtered)
        System.out.println(">>> RAIL Filter - init() called");
        String status = String.format("portalConfigService=%s, serviceDeskManager=%s, projectManager=%s",
            portalConfigService != null ? "OK" : "NULL",
            serviceDeskManager != null ? "OK" : "NULL",
            projectManager != null ? "OK" : "NULL");
        System.out.println(">>> RAIL Filter - Services: " + status);
        log.info("RAIL Filter - Initialization complete. Services: {}", status);
    }

    @Override
    public void destroy() {
        System.out.println(">>> RAIL Filter - destroy() called");
        log.info("RailCustomerPortalRedirectFilter destroyed");
        portalIdCache.clear();
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        // ALWAYS log that filter is being invoked (for debugging)
        if (request instanceof HttpServletRequest) {
            HttpServletRequest hr = (HttpServletRequest) request;
            String uri = hr.getRequestURI();
            // Log ALL requests to verify filter is working
            if (uri.contains("/portal/") || uri.contains("/servicedesk/") || uri.contains("/desk/")) {
                System.out.println(">>> RAIL Filter INVOKED: " + uri);
            }
        }

        // Prevent double-filtering
        if (request.getAttribute(ALREADY_FILTERED) != null) {
            chain.doFilter(request, response);
            return;
        }

        if (!(request instanceof HttpServletRequest) || !(response instanceof HttpServletResponse)) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        request.setAttribute(ALREADY_FILTERED, Boolean.TRUE);

        String path = extractPath(httpRequest);
        String fullUrl = buildFullUrl(httpRequest);

        // Log all portal-related requests with System.out for guaranteed visibility
        boolean isPortalRequest = path.contains("/portal/") || path.contains("/desk/portal/") || path.contains("/servicedesk/");
        if (isPortalRequest) {
            System.out.println(">>> RAIL Filter - Checking path: " + path);
            System.out.println(">>> RAIL Filter - Full URL: " + fullUrl);
            log.info("RAIL Filter - Intercepted portal request: path={}, fullUrl={}", path, fullUrl);
        }

        // Check if we should ignore this redirect
        if (shouldIgnoreRedirect(httpRequest, fullUrl)) {
            if (isPortalRequest) {
                System.out.println(">>> RAIL Filter - IGNORING (shouldIgnoreRedirect=true): " + fullUrl);
                log.info("RAIL Filter - Ignoring redirect (shouldIgnoreRedirect=true): {}", fullUrl);
            }
            chain.doFilter(request, response);
            return;
        }

        if (handleRedirect(httpRequest, httpResponse, path)) {
            return; // Redirect was sent
        }

        if (isPortalRequest) {
            System.out.println(">>> RAIL Filter - No redirect performed, passing through: " + path);
            log.info("RAIL Filter - No redirect performed, passing through: {}", path);
        }

        chain.doFilter(request, response);
    }

    /**
     * Attempt to redirect to RAIL Portal if conditions are met.
     */
    private boolean handleRedirect(HttpServletRequest request, HttpServletResponse response, String path)
        throws IOException {

    String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
    String normalizedPath = normalizePath(path);

    // 0) ALWAYS redirect legacy "homepage" URLs (regardless of isLive or skipRail)
    if (isAlwaysRedirectHomePath(normalizedPath)) {
        String target = appendQueryString(request, contextPath + RAIL_HOME_PATH);

        System.out.println(">>> RAIL Filter - HOME REDIRECT: " + request.getRequestURI() + " -> " + target);
        log.info("RAIL Filter - HOME REDIRECT: {} -> {}", request.getRequestURI(), target);

        response.sendRedirect(target);
        return true;
    }

    // Don't redirect if already going to RAIL Portal
    if (normalizedPath != null && normalizedPath.contains("/customer-rail")) {
        System.out.println(">>> RAIL Filter - Already targeting customer-rail, skipping");
        return false;
    }

    // Don't redirect if skipRail parameter is present (used when portal is NOT Live)
    // NOTE: This does NOT apply to ALWAYS redirect homepages (handled above).
    String skipRail = request.getParameter("skipRail");
    if ("true".equals(skipRail)) {
        System.out.println(">>> RAIL Filter - skipRail=true, allowing OOTB portal");
        return false;
    }

    // ... keep the rest of your existing logic the same ...

        // FIRST: Check if this is a request type URL (e.g., /portal/5/create/123)
        // These need special handling to redirect to RAIL request type page
                String[] requestTypeInfo = extractRequestTypeInfo(path);
        if (requestTypeInfo != null) {
            Integer portalId = parsePortalId(requestTypeInfo[0]);
            String requestTypeId = requestTypeInfo[1];

            if (portalId != null && requestTypeId != null) {
                System.out.println(">>> RAIL Filter - Request type URL detected: portalId=" + portalId + ", requestTypeId=" + requestTypeId);

                String projectKey = getProjectKeyFromPortalId(portalId);
                if (projectKey != null) {
                    Optional<PortalConfigDTO> config = getPortalConfig(projectKey);
                    boolean isLive = config.isPresent() && config.get().isLive();

                    if (isLive) {
                        // Redirect to RAIL request type page
                        String target = contextPath + RAIL_PORTAL_PATH + projectKey + "/requesttype/" + requestTypeId;
                        target = withQueryString(request, target);

                        System.out.println(">>> RAIL Filter - REDIRECTING REQUEST TYPE (RAIL): " + request.getRequestURI() + " -> " + target);
                        log.info("RAIL Filter - REDIRECTING REQUEST TYPE (RAIL): {} -> {}", request.getRequestURI(), target);
                        response.sendRedirect(target);
                        return true;
                    }

                    // NEW: If NOT live and this was a Desk/Refined URL, redirect to OOTB create URL
                    if (path.startsWith("/plugins/servlet/desk/portal/")) {
                        String target = contextPath + OOTB_JSM_PORTAL_PATH + portalId + "/create/" + requestTypeId;
                        target = withQueryString(request, target);

                        System.out.println(">>> RAIL Filter - REDIRECTING REQUEST TYPE (OOTB fallback): " + request.getRequestURI() + " -> " + target);
                        log.info("RAIL Filter - REDIRECTING REQUEST TYPE (OOTB fallback): {} -> {}", request.getRequestURI(), target);
                        response.sendRedirect(target);
                        return true;
                    }
                }
            }

            // If we can't redirect, let it pass through (may 404 if Refined is disabled)
            System.out.println(">>> RAIL Filter - Request type URL but cannot resolve portal/project, passing through");
            return false;
        }

        // SECOND: Check if this is a portal sub-path (issue view, user pages, etc.)
        // We don't have pages to handle these, so pass through to OOTB
        if (isPortalSubPath(path)) {
            System.out.println(">>> RAIL Filter - Portal sub-path detected, passing to OOTB: " + path);
            log.info("RAIL Filter - Portal sub-path detected, passing through: {}", path);
            return false;
        }

        // Try to extract portal ID from various URL patterns
        Integer portalId = extractPortalId(path);
        if (portalId == null) {
            // Log pattern match results for debugging
            boolean jsmMatch = JSM_PORTAL_PATTERN.matcher(path).find();
            boolean railMatch = DESK_PORTAL_RAIL_PATTERN.matcher(path).find();
            boolean baseMatch = DESK_PORTAL_BASE_PATTERN.matcher(path).find();
            System.out.println(">>> RAIL Filter - No portal ID from path: " + path);
            System.out.println(">>> RAIL Filter - Pattern results: JSM=" + jsmMatch + ", Rail=" + railMatch + ", Base=" + baseMatch);
            log.info("RAIL Filter - No portal ID extracted from path: {} (JSM={}, Rail={}, Base={})",
                path, jsmMatch, railMatch, baseMatch);
            return false;
        }

        System.out.println(">>> RAIL Filter - Extracted portal ID: " + portalId + " from path: " + path);
        log.info("RAIL Filter - Extracted portal ID {} from path: {}", portalId, path);

        // Get project key from portal ID
        String projectKey = getProjectKeyFromPortalId(portalId);
        if (projectKey == null) {
            System.out.println(">>> RAIL Filter - No project key for portal ID " + portalId);
            log.info("RAIL Filter - No project key found for portal ID {} (serviceDeskManager={}, projectManager={})",
                portalId,
                serviceDeskManager != null ? "available" : "NULL",
                projectManager != null ? "available" : "NULL");
            return false;
        }

        System.out.println(">>> RAIL Filter - Portal " + portalId + " -> Project " + projectKey);
        log.info("RAIL Filter - Mapped portal ID {} to project key: {}", portalId, projectKey);

        // Check if RAIL portal is Live for this project
              Optional<PortalConfigDTO> config = getPortalConfig(projectKey);
        boolean isLive = config.isPresent() && config.get().isLive();

        System.out.println(">>> RAIL Filter - Project " + projectKey + " isLive=" + isLive);
        log.info("RAIL Filter - Portal config for {}: isLive={}", projectKey, isLive);

        // NEW: If this is a Desk/Refined URL and portal is NOT live, fall back to OOTB portal URL
        if (!isLive && path.startsWith("/plugins/servlet/desk/portal/")) {
            String target = contextPath + OOTB_JSM_PORTAL_PATH + portalId;
            target = withQueryString(request, target);

            System.out.println(">>> RAIL Filter - REDIRECTING (OOTB fallback): " + request.getRequestURI() + " -> " + target);
            log.info("RAIL Filter - REDIRECTING (OOTB fallback): {} -> {}", request.getRequestURI(), target);
            response.sendRedirect(target);
            return true;
        }

        if (!isLive) {
            // For non-desk URLs (e.g. /servicedesk/customer/portal/{id}) keep your current behavior: pass through
            System.out.println(">>> RAIL Filter - Portal NOT live, passing through");
            log.info("RAIL Filter - Portal for project {} is NOT live, passing through", projectKey);
            return false;
        }

        // Build redirect URL
        String target = contextPath + RAIL_PORTAL_PATH + projectKey;

        // Preserve query string
        String queryString = request.getQueryString();
        if (queryString != null && !queryString.isEmpty()) {
            target = target + "?" + queryString;
        }

        System.out.println(">>> RAIL Filter - REDIRECTING: " + request.getRequestURI() + " -> " + target);
        log.info("RAIL Filter - REDIRECTING: {} -> {}", request.getRequestURI(), target);
        response.sendRedirect(target);
        return true;
    }

    /**
     * Extract portal ID and request type ID from request type URLs.
     * Returns String[2] with [portalId, requestTypeId] or null if not a request type URL.
     */
    private String[] extractRequestTypeInfo(String path) {
        // Try JSM request type pattern: /servicedesk/customer/portal/{portalId}/create/{requestTypeId}
        Matcher jsmMatcher = JSM_REQUEST_TYPE_PATTERN.matcher(path);
        if (jsmMatcher.find()) {
            System.out.println(">>> RAIL Filter - Matched JSM request type pattern");
            return new String[] { jsmMatcher.group(1), jsmMatcher.group(2) };
        }

        // Try Desk/Refined request type pattern: /plugins/servlet/desk/portal/{portalId}/create/{requestTypeId}
        Matcher deskMatcher = DESK_REQUEST_TYPE_PATTERN.matcher(path);
        if (deskMatcher.find()) {
            System.out.println(">>> RAIL Filter - Matched Desk request type pattern");
            return new String[] { deskMatcher.group(1), deskMatcher.group(2) };
        }

        return null;
    }

    /**
     * Extract the numeric portal ID from the request path.
     * Uses find() instead of matches() to be more lenient with path matching.
     */
    private Integer extractPortalId(String path) {
        // Try desk portal rail pattern first (more specific)
        Matcher railMatcher = DESK_PORTAL_RAIL_PATTERN.matcher(path);
        if (railMatcher.find()) {
            System.out.println(">>> RAIL Filter - Matched RAIL pattern, group(1)=" + railMatcher.group(1));
            return parsePortalId(railMatcher.group(1));
        }

        // Try desk portal base pattern
        Matcher baseMatcher = DESK_PORTAL_BASE_PATTERN.matcher(path);
        if (baseMatcher.find()) {
            System.out.println(">>> RAIL Filter - Matched BASE pattern, group(1)=" + baseMatcher.group(1));
            return parsePortalId(baseMatcher.group(1));
        }

        // Try JSM portal pattern
        Matcher jsmMatcher = JSM_PORTAL_PATTERN.matcher(path);
        if (jsmMatcher.find()) {
            System.out.println(">>> RAIL Filter - Matched JSM pattern, group(1)=" + jsmMatcher.group(1));
            return parsePortalId(jsmMatcher.group(1));
        }

        return null;
    }

    private Integer parsePortalId(String portalIdStr) {
        if (portalIdStr == null || portalIdStr.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(portalIdStr);
        } catch (NumberFormatException e) {
            log.debug("Invalid portal ID format: {}", portalIdStr);
            return null;
        }
    }

    /**
     * Check if the path is a portal sub-path that we should NOT redirect.
     * Sub-paths include: issue view (e.g., /WMPR-8149), create pages, etc.
     * We only want to redirect the base portal URL, not sub-pages.
     *
     * Paths we SHOULD redirect (return false):
     * - /plugins/servlet/desk/portal/14
     * - /plugins/servlet/desk/portal/14/
     * - /plugins/servlet/desk/portal/14/rail
     * - /plugins/servlet/desk/portal/14/rail/
     * - /servicedesk/customer/portal/14
     * - /servicedesk/customer/portal/14/
     *
     * Paths we should NOT redirect (return true):
     * - /plugins/servlet/desk/portal/14/WMPR-8149 (issue view)
     * - /plugins/servlet/desk/portal/14/create/123 (create page)
     * - /plugins/servlet/desk/portal/14/user/requests (user requests)
     */
    private boolean isPortalSubPath(String path) {
        // Pattern to match portal base paths that we SHOULD redirect
        // These are paths that end with just the portal ID, optional slash, or /rail

        // Check for /plugins/servlet/desk/portal/{id} paths
        if (path.contains("/plugins/servlet/desk/portal/")) {
            // Extract everything after /plugins/servlet/desk/portal/
            String remainder = path.substring(path.indexOf("/plugins/servlet/desk/portal/") + "/plugins/servlet/desk/portal/".length());

            // Remainder should be: "14", "14/", "14/rail", or "14/rail/"
            // If it has anything else, it's a sub-path
            if (remainder.matches("^\\d+$") || remainder.matches("^\\d+/$")) {
                // Just portal ID or portal ID with trailing slash - allow redirect
                return false;
            }
            if (remainder.matches("^\\d+/rail$") || remainder.matches("^\\d+/rail/$")) {
                // Portal ID with /rail suffix - allow redirect
                return false;
            }
            // Has additional content after portal ID - this is a sub-path
            System.out.println(">>> RAIL Filter - isPortalSubPath check: remainder='" + remainder + "' -> IS sub-path");
            return true;
        }

        // Check for /servicedesk/customer/portal/{id} paths
        if (path.contains("/servicedesk/customer/portal/")) {
            String remainder = path.substring(path.indexOf("/servicedesk/customer/portal/") + "/servicedesk/customer/portal/".length());

            // Remainder should be just the portal ID with optional trailing slash
            if (remainder.matches("^\\d+$") || remainder.matches("^\\d+/$")) {
                return false;
            }
            // Has additional content - sub-path
            System.out.println(">>> RAIL Filter - isPortalSubPath check: remainder='" + remainder + "' -> IS sub-path");
            return true;
        }

        // Not a recognized portal path pattern - don't flag as sub-path
        return false;
    }

    /**
     * Get project key from portal ID using cache and ServiceDeskManager.
     */
    private String getProjectKeyFromPortalId(int portalId) {
        // Check cache first
        String cached = portalIdCache.get(portalId);
        if (cached != null) {
            System.out.println(">>> RAIL Filter - Cache hit: portal " + portalId + " -> " + cached);
            return cached;
        }

        if (serviceDeskManager == null) {
            System.out.println(">>> RAIL Filter - ServiceDeskManager is NULL!");
            return null;
        }

        if (projectManager == null) {
            System.out.println(">>> RAIL Filter - ProjectManager is NULL!");
            return null;
        }

        try {
            System.out.println(">>> RAIL Filter - Looking up service desk for portal ID " + portalId);
            ServiceDesk serviceDesk = serviceDeskManager.getServiceDeskById(portalId);
            if (serviceDesk == null) {
                System.out.println(">>> RAIL Filter - No service desk found for portal ID " + portalId);
                return null;
            }

            long projectId = serviceDesk.getProjectId();
            System.out.println(">>> RAIL Filter - Service desk " + portalId + " maps to project ID " + projectId);

            Project project = projectManager.getProjectObj(projectId);
            if (project == null) {
                System.out.println(">>> RAIL Filter - No project found for project ID " + projectId);
                return null;
            }

            String projectKey = project.getKey();
            // Cache the result
            portalIdCache.put(portalId, projectKey);
            System.out.println(">>> RAIL Filter - SUCCESS: portal " + portalId + " -> project " + projectKey);
            return projectKey;

        } catch (Exception e) {
            System.out.println(">>> RAIL Filter - Exception: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return null;
        }
    }

    /**
     * Get portal configuration for a project.
     */
    private Optional<PortalConfigDTO> getPortalConfig(String projectKey) {
        if (portalConfigService == null) {
            System.out.println(">>> RAIL Filter - PortalConfigService is NULL!");
            return Optional.empty();
        }

        try {
            return portalConfigService.getPortalConfig(projectKey);
        } catch (Exception e) {
            System.out.println(">>> RAIL Filter - Error getting config: " + e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Determine if redirect should be ignored based on request characteristics.
     * Similar logic to the old filter - don't redirect AJAX, iframe, API, or form requests.
     */
    private boolean shouldIgnoreRedirect(HttpServletRequest request, String fullUrl) {
        // Only process GET requests
        if (!"GET".equals(request.getMethod())) {
            log.debug("Ignoring non-GET request: {}", request.getMethod());
            return true;
        }

        // Check for iframe context
        String secFetchDest = request.getHeader("Sec-Fetch-Dest");
        if ("iframe".equals(secFetchDest)) {
            log.debug("Ignoring iframe request");
            return true;
        }

        // Check for AJAX requests
        String xRequestedWith = request.getHeader("X-Requested-With");
        if ("XMLHttpRequest".equals(xRequestedWith)) {
            log.debug("Ignoring AJAX request");
            return true;
        }

        // Check for API/REST calls
        if (fullUrl.contains("/rest/") || fullUrl.contains("/api/")) {
            log.debug("Ignoring API/REST call");
            return true;
        }

        // Check for embedded/iframe flags
        if (fullUrl.contains("embedded=true") || fullUrl.contains("decorator=none")) {
            log.debug("Ignoring embedded request");
            return true;
        }

        if (fullUrl.contains("noRedirect=true") || fullUrl.contains("iframeContext=true")) {
            log.debug("Ignoring request with noRedirect flag");
            return true;
        }

        // Check for form creation URLs - but NOT request type URLs which we handle specially
        // Request type URLs like /portal/5/create/123 should be redirected to RAIL
        // Only ignore /create/ URLs that are NOT request type patterns
        if (fullUrl.contains("/create/")) {
            // Check if this is a request type URL (which we want to redirect)
            String path = extractPath(request);
            if (extractRequestTypeInfo(path) != null) {
                // This is a request type URL - don't ignore it, let handleRedirect process it
                log.debug("Request type URL detected, will process for redirect");
                return false;
            }
            // Other /create/ URLs should be ignored (e.g., /create/some-other-thing)
            log.debug("Ignoring non-request-type creation URL");
            return true;
        }

        // Check for user management URLs
        if (fullUrl.matches(".*?/portal/\\d+/user/.*")) {
            log.debug("Ignoring user management URL");
            return true;
        }

        // Check for feedback/unsubscribe URLs
        if (fullUrl.contains("/feedback") || fullUrl.contains("/unsubscribe")) {
            log.debug("Ignoring feedback/unsubscribe URL");
            return true;
        }

        return false;
    }

    /**
     * Extract path from request, removing context path.
     */
    private String extractPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    /**
     * Build full URL for logging and pattern matching.
     */
    private String buildFullUrl(HttpServletRequest request) {
        StringBuffer url = request.getRequestURL();
        String queryString = request.getQueryString();
        if (queryString != null) {
            url.append("?").append(queryString);
        }
        return url.toString();
    }
}
