    // Pattern: Refined/Desk Issue View URL (legacy)
    // Matches: /plugins/servlet/desk/portal/14/WMPR-8149 or trailing slash
    // NOTE: allow lowercase too, Jira will normalize, and user bookmarks/emails can vary
    private static final Pattern DESK_ISSUE_KEY_PATTERN = Pattern.compile(
        "/plugins/servlet/desk/portal/(\\d+)/([A-Za-z][A-Za-z0-9]+-\\d+)(/)?$"
    );

        // NEW: Desk/Refined issue view deep link -> redirect to OOTB JSM issue view
        // Example: /plugins/servlet/desk/portal/14/WMPR-8149
        Matcher deskIssueMatcher = DESK_ISSUE_KEY_PATTERN.matcher(path);
        if (deskIssueMatcher.find()) {
            Integer portalId = parsePortalId(deskIssueMatcher.group(1));
            String issueKey = deskIssueMatcher.group(2);

            if (portalId != null && issueKey != null && !issueKey.isEmpty()) {
                String target = contextPath + OOTB_JSM_PORTAL_PATH + portalId + "/" + issueKey;
                target = withQueryString(request, target);

                System.out.println(">>> RAIL Filter - REDIRECTING (OOTB issue fallback): " + request.getRequestURI() + " -> " + target);
                log.info("RAIL Filter - REDIRECTING (OOTB issue fallback): {} -> {}", request.getRequestURI(), target);

                response.sendRedirect(target);
                return true;
            }
        }