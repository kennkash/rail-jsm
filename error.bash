rm-bundled-plugins-7.1.6.pom
[INFO] ------------------------------------------------------------------------
[INFO] BUILD FAILURE
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  01:54 min
[INFO] Finished at: 2026-03-26T13:27:38-05:00
[INFO] ------------------------------------------------------------------------
[ERROR] Failed to execute goal on project rail-portal-plugin: Could not collect dependencies for project com.samsungbuilder.jsm:rail-portal-plugin:atlassian-plugin:2.0.15-J10
[ERROR] Failed to read artifact descriptor for com.atlassian.webhooks:atlassian-webhooks-api:jar:8.1.6
[ERROR]         Caused by: The following artifacts could not be resolved: com.atlassian.platform.dependencies:platform-bundled-plugins:pom:7.1.6 (absent): Could not transfer artifact com.atlassian.platform.dependencies:platform-bundled-plugins:pom:7.1.6 from/to central (https://repo1.maven.apache.org/maven2): repo1.maven.apache.org: Temporary failure in name resolution
[ERROR] Failed to read artifact descriptor for com.atlassian.webhooks:atlassian-webhooks-spi:jar:8.1.6
[ERROR]         Caused by: The following artifacts could not be resolved: com.atlassian.platform.dependencies:platform-bundled-plugins:pom:7.1.6 (absent): com.atlassian.platform.dependencies:platform-bundled-plugins:pom:7.1.6 failed to transfer from https://repo1.maven.apache.org/maven2 during a previous attempt. This failure was cached in the local repository and resolution is not reattempted until the update interval of central has elapsed or updates are forced. Original error: Could not transfer artifact com.atlassian.platform.dependencies:platform-bundled-plugins:pom:7.1.6 from/to central (https://repo1.maven.apache.org/maven2): repo1.maven.apache.org: Temporary failure in name resolution
[ERROR] 
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/DependencyResolutionException
