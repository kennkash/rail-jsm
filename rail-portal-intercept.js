/* rail-at-sas/backend/src/main/resources/frontend/rail-portal-intercept.js  */
/**
 * RAIL Portal Link Interceptor
 *
 * This script runs on OOTB JSM customer portal pages and intercepts
 * navigation to portal root URLs, forcing a full page reload so the
 * server-side filter can redirect to RAIL Portal when Live.
 *
 * IMPORTANT: Only intercepts navigation TO portal roots, not FROM them.
 * If we're already on a portal root, the server had its chance to redirect.
 */
(function() {
    'use strict';

    // Guard against multiple script loads
    if (window.__RAIL_INTERCEPTOR_LOADED__) {
        console.log('>>> RAIL Portal Interceptor: Already loaded, skipping');
        return;
    }
    window.__RAIL_INTERCEPTOR_LOADED__ = true;

    console.log('>>> RAIL Portal Interceptor: Initializing...');

    function normalizePath(path) {
        if (!path) return path;
        return (path.length > 1 && path.endsWith('/')) ? path.slice(0, -1) : path;
    }

    /**
     * Check if a URL path is a portal root that should trigger RAIL redirect
     */
    function isPortalRootUrl(pathname) {
        if (!pathname) return false;

        // Strip query string if present
        var queryIndex = pathname.indexOf('?');
        if (queryIndex > -1) {
            pathname = pathname.substring(0, queryIndex);
        }

        // Strip hash if present
        var hashIndex = pathname.indexOf('#');
        if (hashIndex > -1) {
            pathname = pathname.substring(0, hashIndex);
        }

        // Patterns for portal root URLs that should redirect to RAIL
        // These ONLY match the portal root, not sub-pages
        var portalRootPatterns = [
            /\/plugins\/servlet\/desk\/portal\/\d+\/?$/,
            /\/plugins\/servlet\/desk\/portal\/\d+\/rail\/?$/,
            /\/servicedesk\/customer\/portal\/\d+\/?$/
        ];

        for (var i = 0; i < portalRootPatterns.length; i++) {
            if (portalRootPatterns[i].test(pathname)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a URL path is a JSM "portals home" that should trigger RAIL home redirect
     * (forces full page request so server-side filter can redirect)
     */
    function isPortalsHomeUrl(pathname) {
        if (!pathname) return false;

        // Strip query string if present
        var queryIndex = pathname.indexOf('?');
        if (queryIndex > -1) {
            pathname = pathname.substring(0, queryIndex);
        }

        // Strip hash if present
        var hashIndex = pathname.indexOf('#');
        if (hashIndex > -1) {
            pathname = pathname.substring(0, hashIndex);
        }

        // Match exactly /servicedesk/customer/portals or /servicedesk/customer/portals/
        return /\/servicedesk\/customer\/portals\/?$/.test(pathname);
    }

    /**
     * Extract pathname from href (handles relative and absolute URLs)
     * Strips query parameters and hash for pattern matching
     */
    function extractPathname(href) {
        if (!href) return null;

        // Handle absolute URLs
        if (href.indexOf('http') === 0) {
            try {
                return new URL(href).pathname;
            } catch (e) {
                return null;
            }
        }

        // Handle relative URLs - strip query string and hash
        var path = href;
        var queryIndex = path.indexOf('?');
        if (queryIndex > -1) {
            path = path.substring(0, queryIndex);
        }
        var hashIndex = path.indexOf('#');
        if (hashIndex > -1) {
            path = path.substring(0, hashIndex);
        }
        return path;
    }

    // Check if we're CURRENTLY on a portal root URL
    // If so, the server already had a chance to redirect - don't cause loops
    var currentPathname = window.location.pathname;
    var isCurrentlyOnPortalRoot = isPortalRootUrl(currentPathname);

    if (isCurrentlyOnPortalRoot) {
        console.log('>>> RAIL Portal Interceptor: Currently on portal root, server had chance to redirect. Skipping interceptors to prevent loops.');
        // Still attach click handler for navigation to OTHER portals, but skip polling/popstate
    }

    // ========== 1. Click Handler ==========
    /**
     * Handle link clicks and force full page navigation for portal roots
     */
    function handleLinkClick(event) {
        // More reliable than manual parent walking (supports nested spans/icons)
        var anchor = event.target && event.target.closest ? event.target.closest('a[href]') : null;
        if (!anchor) return;

        var href = anchor.getAttribute('href');
        if (!href) return;

        // Skip if it's already a full URL to a different domain
        if (href.indexOf('http') === 0 && href.indexOf(window.location.origin) !== 0) {
            return;
        }

        var pathname = extractPathname(href);

        // Skip if navigating to the SAME URL we're already on (normalize trailing slashes)
        if (normalizePath(pathname) === normalizePath(currentPathname)) {
            return;
        }

        if (isPortalRootUrl(pathname) || isPortalsHomeUrl(pathname)) {
            console.log('>>> RAIL Interceptor: Click intercepted for portal root/home:', pathname);
            event.preventDefault();
            event.stopPropagation();
            event.stopImmediatePropagation();

            // Force full page navigation
            currentPathname = pathname; // keep local state consistent
            window.location.href = href;
            return false;
        }
    }

    // Attach click handler using capture phase to intercept before Jira's SPA router
    document.addEventListener('click', handleLinkClick, true);

    // ========== 2. History API Interception ==========
    /**
     * Monkey-patch history.pushState and history.replaceState to intercept
     * JSM's SPA navigation before it happens
     *
     * ONLY intercept if navigating to a DIFFERENT portal root than current
     */
    var originalPushState = history.pushState;
    var originalReplaceState = history.replaceState;

    function interceptHistoryMethod(original) {
        return function(state, title, url) {
            if (url) {
                var pathname = extractPathname(url.toString());

                // Skip if navigating to the SAME URL we're already on (normalize trailing slashes)
                if (normalizePath(pathname) === normalizePath(currentPathname)) {
                    return original.apply(history, arguments);
                }

                // Only intercept if navigating to a portal ROOT or portals HOME
                if (isPortalRootUrl(pathname) || isPortalsHomeUrl(pathname)) {
                    console.log('>>> RAIL Interceptor: History API intercepted for portal root/home:', pathname);
                    // Force full page navigation instead of SPA navigation
                    currentPathname = pathname; // keep local state consistent
                    window.location.href = url;
                    return;
                }
            }
            return original.apply(history, arguments);
        };
    }

    history.pushState = interceptHistoryMethod(originalPushState);
    history.replaceState = interceptHistoryMethod(originalReplaceState);

    // ========== 3. Popstate Handler (Back/Forward) - DISABLED if on portal root ==========
    /**
     * Handle browser back/forward navigation
     * ONLY if we're NOT currently on a portal root (to prevent loops)
     */
    if (!isCurrentlyOnPortalRoot) {
        window.addEventListener('popstate', function() {
            var newPathname = window.location.pathname;
            // Only reload if we navigated to a DIFFERENT portal root/home
            if (normalizePath(newPathname) !== normalizePath(currentPathname) &&
                (isPortalRootUrl(newPathname) || isPortalsHomeUrl(newPathname))) {
                console.log('>>> RAIL Interceptor: Popstate to different portal root/home, reloading');
                window.location.reload();
            }
        });
    }

    // ========== 4. URL Polling - DISABLED ==========
    // Removed URL polling fallback as it can cause infinite loops
    // The click handler and history interception should be sufficient

    console.log('>>> RAIL Portal Interceptor: Handlers attached (currentlyOnPortalRoot=' + isCurrentlyOnPortalRoot + ')');
})();
