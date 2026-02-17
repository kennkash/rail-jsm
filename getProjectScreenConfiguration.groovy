import com.onresolve.scriptrunner.runner.rest.common.CustomEndpointDelegate
import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.FieldManager
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenScheme
import com.atlassian.jira.issue.fields.screen.issuetype.IssueTypeScreenSchemeManager
import com.atlassian.jira.issue.fields.config.manager.IssueTypeSchemeManager
import com.atlassian.jira.issue.fields.screen.FieldScreenManager
import com.atlassian.jira.project.ProjectManager
import com.atlassian.jira.config.IssueTypeManager
import groovy.json.JsonBuilder
import groovy.transform.BaseScript
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response

@BaseScript CustomEndpointDelegate delegate

getProjectScreenConfiguration(httpMethod: "GET", groups: ["jira-software-users", "jira-servicedesk-users", "jira-administrators"]) { MultivaluedMap queryParams, String body ->
    def projectKey = queryParams.getFirst("projectKey")?.toString()

    if (!projectKey) {
        return Response.status(400)
                .entity(new JsonBuilder([error: "The 'projectKey' query parameter is required."]).toString())
                .build()
    }

    def projectManager = ComponentAccessor.getProjectManager()
    def issueTypeScreenSchemeManager = ComponentAccessor.getComponent(IssueTypeScreenSchemeManager)
    def issueTypeManager = ComponentAccessor.getComponent(IssueTypeManager)
    def screenManager = ComponentAccessor.getComponent(FieldScreenManager)
    def fieldManager = ComponentAccessor.getComponent(FieldManager)
    def issueTypeSchemeManager = ComponentAccessor.getIssueTypeSchemeManager()

    // Find the project by key
    def project = projectManager.getProjectByCurrentKey(projectKey)
    if (!project) {
        return Response.status(404)
                .entity(new JsonBuilder([error: "Project with key '${projectKey}' does not exist."]).toString())
                .build()
    }

    def issueTypeScreenScheme = issueTypeScreenSchemeManager.getIssueTypeScreenScheme(project)
    if (!issueTypeScreenScheme) {
        return Response.status(404)
                .entity(new JsonBuilder([error: "No Issue Type Screen Scheme found for project '${projectKey}'."]).toString())
                .build()
    }

    def screenDetailsMap = [:].withDefault { [issueTypes: [], issueTypeUrls: [], screens: [], shared: false, projects: []] }

    // Get all issue types in the project
    def allIssueTypes = project.getIssueTypes()

    // Get default issue type
    def issueTypeScheme = issueTypeSchemeManager.getDefaultIssueType(project)

    def getFieldInfo = { String fieldId ->
        def field = fieldManager.getField(fieldId)

        if (!field) {
            return [id: fieldId, name: fieldId, type: "Unknown"]
        }

        def fieldName = field.getName() ?: fieldId
        def fieldType = "Unknown"

        if (field instanceof CustomField) {
            fieldType = field.getCustomFieldType().getName()
        } else {
            fieldType = "System field"
            // switch (field.getId()) {
            //     case "summary"       : fieldType = "Text Field (Summary)"; break
            //     case "description"   : fieldType = "Text Area (Description)"; break
            //     case "priority"      : fieldType = "Select List (single choice)"; break
            //     case "assignee"      : fieldType = "User Picker"; break
            //     case "reporter"      : fieldType = "User Picker"; break
            //     case "duedate"       : fieldType = "Date Picker"; break
            //     // add any other system‑field mappings you want …
            //     default:
            //         fieldType = field.getClass().simpleName
            // }
        }

        return [id: fieldId, name: fieldName, type: fieldType]
    }


    // For each entity in the screen scheme
    issueTypeScreenScheme.getEntities().each { entity ->
        def issueTypeId = entity.getIssueTypeId()
        def issueType = issueTypeId ? issueTypeManager.getIssueType(issueTypeId) : null

        def screenScheme = entity.getFieldScreenScheme()

        // Collect projects using the screen scheme
        def projectsUsingScheme = []
        issueTypeScreenSchemeManager.getIssueTypeScreenSchemes(screenScheme).each { scheme ->
            scheme.getProjects().each { p ->
                projectsUsingScheme.add(p.key)
            }
        }
        projectsUsingScheme = projectsUsingScheme.unique()

        // Determine if the screen scheme is shared
        def isShared = projectsUsingScheme.size() > 1

        def screenDetails = []
        screenScheme.getFieldScreenSchemeItems().each { item ->
            def screen = item.getFieldScreen()
            def operation = item.getIssueOperationName()
            // def fields = screen.getTabs().collectMany { tab ->
            //     tab.getFieldScreenLayoutItems().collect { fieldLayoutItem ->
            //         fieldManager.getField(fieldLayoutItem.getFieldId())?.name ?: fieldLayoutItem.getFieldId()
            //     }
            // }

            def tabs = screen.getTabs().collect { tab ->
                def fieldInfos = tab.getFieldScreenLayoutItems().collect { layoutItem ->
                    getFieldInfo(layoutItem.getFieldId())
                }
                [
                    tabName : tab.getName(),
                    fields  : fieldInfos
                ]
            }

            screenDetails << [
                screenName: screen.name,
                operationName: operation,
                tabs: tabs
            ]
        }

        // Group issue types by screen scheme name
        def screenSchemeEntry = screenDetailsMap[screenScheme.name]
        if (issueType) {
            screenSchemeEntry.issueTypes << issueType.name
            screenSchemeEntry.issueTypeUrls << issueType.iconUrl
        }
        screenSchemeEntry.screens = screenDetails

        screenSchemeEntry.shared = isShared
        screenSchemeEntry.projects = projectsUsingScheme
    }

    // Identify the default screen scheme
    def defaultScreenSchemeName = issueTypeScreenScheme.getEntities().find { it.getIssueTypeId() == null }?.getFieldScreenScheme()?.name ?: "Default Screen Scheme"

    // Find all issue types that are not explicitly mapped to a screen scheme
    def defaultIssueTypes = allIssueTypes.findAll { issueType ->
        !issueTypeScreenScheme.getEntities().any { entity ->
            entity.getIssueTypeId() == issueType.getId()
        }
    }

    // Add default issue types to the default screen scheme entry
    if (defaultIssueTypes) {
        def defaultIssueTypeNames = defaultIssueTypes.collect { it.name }.join(', ')
        def defaultIssueTypeUrls = defaultIssueTypes.collect { it.iconUrl }.join(', ')
        screenDetailsMap[defaultScreenSchemeName].issueTypes << defaultIssueTypeNames
        screenDetailsMap[defaultScreenSchemeName].issueTypeUrls << defaultIssueTypeUrls
    }

    // Define the specific operations
    def specificOperations = ['admin.issue.operations.create', 'admin.issue.operations.edit', 'admin.issue.operations.view']

    // Process each screen scheme entry to handle redundancy and adjust default operations
    screenDetailsMap.each { screenSchemeName, details ->
        def operations = details.screens.collect { it.operationName }

        if (operations.contains('admin.common.words.default')) {
            def defaultScreens = details.screens.findAll { it.operationName == 'admin.common.words.default' }
            def otherScreens = details.screens.findAll { it.operationName != 'admin.common.words.default' }

            if (otherScreens.size() == specificOperations.size()) {
                // If all specific operations are covered, remove the default screen
                details.screens = details.screens.findAll { it.operationName != 'admin.common.words.default' }
            } else {
                // Adjust the default screen to cover unmapped operations
                def mappedOperations = otherScreens.collect { it.operationName }
                def unmappedOperations = specificOperations.findAll { !mappedOperations.contains(it) }

                if (unmappedOperations) {
                    defaultScreens.each { defaultScreen ->
                        defaultScreen.operationName = unmappedOperations.join(', ')
                    }
                } else {
                    // If all specific operations are covered, remove the default screen
                    details.screens = details.screens.findAll { it.operationName != 'admin.common.words.default' }
                }
            }
        }
    }

    def issueTypeScreenDetails = screenDetailsMap.collect { screenSchemeName, details ->
        [
            issueType: details.issueTypes.join(', '),
            issueTypeUrls: details.issueTypeUrls.join(', '),
            screenSchemeName: screenSchemeName,
            screens: details.screens,
            shared: details.shared,
            projects: details.projects.join(', ')
            
        ]
    }

    def result = [
        projectKey: project.key,
        projectName: project.name,
        issueTypeScreenScheme: issueTypeScreenScheme.name,
        issueTypeScreenDetails: issueTypeScreenDetails,
        default_screen: defaultScreenSchemeName,
        default_issuetype: issueTypeScheme?.name ?: "None"
    ]

    return Response.ok(new JsonBuilder(result).toString()).build()
}


To hit this API for example: https://jira.yourinstance.com/rest/scriptrunner/latest/custom/getProjectScreenConfiguration?projectKey=WMPR

Then the response looks like this: 
{
  "projectKey": "WMPR",
  "projectName": "Atlassian Platform Requests",
  "issueTypeScreenScheme": "WMPR: Jira Service Desk Issue Type Screen Scheme",
  "issueTypeScreenDetails": [
    {
      "issueType": "New Feature, Ticket, Support, Service Request, New User, Sub-task, New Project",
      "issueTypeUrls": "/secure/viewavatar?size=xsmall&avatarId=11141&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=10321&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=11142&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=17812&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=11603&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=10316&avatarType=issuetype, /secure/viewavatar?size=xsmall&avatarId=10311&avatarType=issuetype",
      "screenSchemeName": "WMPR: Jira Service Desk Screen Scheme",
      "screens": [
        {
          "screenName": "WMPR: Jira Service Desk Screen",
          "operationName": "admin.issue.operations.create, admin.issue.operations.edit, admin.issue.operations.view",
          "tabs": [
            {
              "tabName": "Default",
              "fields": [
                {
                  "id": "summary",
                  "name": "Summary",
                  "type": "System field"
                },
                {
                  "id": "customfield_14410",
                  "name": "Acknowledgement",
                  "type": "Radio Buttons"
                },
                {
                  "id": "issuetype",
                  "name": "Issue Type",
                  "type": "System field"
                },
                {
                  "id": "reporter",
                  "name": "Reporter",
                  "type": "System field"
                },
                {
                  "id": "components",
                  "name": "Component/s",
                  "type": "System field"
                },
                {
                  "id": "attachment",
                  "name": "Attachment",
                  "type": "System field"
                },
                {
                  "id": "duedate",
                  "name": "Due Date",
                  "type": "System field"
                },
                {
                  "id": "description",
                  "name": "Description",
                  "type": "System field"
                },
                {
                  "id": "issuelinks",
                  "name": "Linked Issues",
                  "type": "System field"
                },
                {
                  "id": "assignee",
                  "name": "Assignee",
                  "type": "System field"
                },
                {
                  "id": "priority",
                  "name": "Priority",
                  "type": "System field"
                },
                {
                  "id": "labels",
                  "name": "Labels",
                  "type": "System field"
                },
                {
                  "id": "customfield_10502",
                  "name": "Request participants",
                  "type": "Request Participants"
                },
                {
                  "id": "customfield_10508",
                  "name": "Approvers",
                  "type": "User Picker (multiple users)"
                },
                {
                  "id": "customfield_10504",
                  "name": "Organizations",
                  "type": "Organizations"
                },
                {
                  "id": "customfield_11122",
                  "name": "NT user ID",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_11146",
                  "name": "Team",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "security",
                  "name": "Security Level",
                  "type": "System field"
                },
                {
                  "id": "customfield_10812",
                  "name": "Point of Contact(s)",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_10503",
                  "name": "Customer Request Type",
                  "type": "Customer Request Type Custom Field"
                },
                {
                  "id": "customfield_11810",
                  "name": "Space Key",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_11811",
                  "name": "User(s)",
                  "type": "User Picker (multiple users)"
                },
                {
                  "id": "customfield_10811",
                  "name": "Reference URL",
                  "type": "URL Field"
                },
                {
                  "id": "customfield_13023",
                  "name": "Application",
                  "type": "Checkboxes"
                },
                {
                  "id": "customfield_10117",
                  "name": "Flagged",
                  "type": "Checkboxes"
                },
                {
                  "id": "customfield_14600",
                  "name": "Groups",
                  "type": "Groups"
                },
                {
                  "id": "customfield_14900",
                  "name": "Options",
                  "type": "Radio Buttons"
                },
                {
                  "id": "customfield_14901",
                  "name": "Project Lead",
                  "type": "User Picker (single user)"
                },
                {
                  "id": "customfield_14902",
                  "name": "Admin(s)",
                  "type": "User Picker (multiple users)"
                },
                {
                  "id": "customfield_11861",
                  "name": "Affected Location",
                  "type": "Select List (multiple choices)"
                },
                {
                  "id": "customfield_15707",
                  "name": "Template Field 1",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_12501",
                  "name": "Key Info",
                  "type": "Text Field (multi-line)"
                },
                {
                  "id": "customfield_11534",
                  "name": "Type of Request",
                  "type": "Select List (single choice)"
                },
                {
                  "id": "customfield_16200",
                  "name": "Jira Project",
                  "type": "Multiple Custom Picker"
                },
                {
                  "id": "customfield_15207",
                  "name": "Explanation",
                  "type": "Text Field (multi-line)"
                },
                {
                  "id": "customfield_11542",
                  "name": "Description of Request",
                  "type": "Text Field (multi-line)"
                }
              ]
            }
          ]
        }
      ],
      "shared": false,
      "projects": "WMPR"
    },
    {
      "issueType": "Change",
      "issueTypeUrls": "/secure/viewavatar?size=xsmall&avatarId=11146&avatarType=issuetype",
      "screenSchemeName": "Change Management Screen Scheme",
      "screens": [
        {
          "screenName": "WMPR Change Screen",
          "operationName": "admin.issue.operations.create, admin.issue.operations.edit, admin.issue.operations.view",
          "tabs": [
            {
              "tabName": "Default",
              "fields": [
                {
                  "id": "summary",
                  "name": "Summary",
                  "type": "System field"
                },
                {
                  "id": "issuetype",
                  "name": "Issue Type",
                  "type": "System field"
                },
                {
                  "id": "reporter",
                  "name": "Reporter",
                  "type": "System field"
                },
                {
                  "id": "components",
                  "name": "Component/s",
                  "type": "System field"
                },
                {
                  "id": "attachment",
                  "name": "Attachment",
                  "type": "System field"
                },
                {
                  "id": "duedate",
                  "name": "Due Date",
                  "type": "System field"
                },
                {
                  "id": "description",
                  "name": "Description",
                  "type": "System field"
                },
                {
                  "id": "issuelinks",
                  "name": "Linked Issues",
                  "type": "System field"
                },
                {
                  "id": "assignee",
                  "name": "Assignee",
                  "type": "System field"
                },
                {
                  "id": "priority",
                  "name": "Priority",
                  "type": "System field"
                },
                {
                  "id": "labels",
                  "name": "Labels",
                  "type": "System field"
                },
                {
                  "id": "customfield_10502",
                  "name": "Request participants",
                  "type": "Request Participants"
                },
                {
                  "id": "customfield_10508",
                  "name": "Approvers",
                  "type": "User Picker (multiple users)"
                },
                {
                  "id": "customfield_10504",
                  "name": "Organizations",
                  "type": "Organizations"
                },
                {
                  "id": "customfield_11146",
                  "name": "Team",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "security",
                  "name": "Security Level",
                  "type": "System field"
                },
                {
                  "id": "customfield_10812",
                  "name": "Point of Contact(s)",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_10503",
                  "name": "Customer Request Type",
                  "type": "Customer Request Type Custom Field"
                },
                {
                  "id": "customfield_14600",
                  "name": "Groups",
                  "type": "Groups"
                },
                {
                  "id": "customfield_11861",
                  "name": "Affected Location",
                  "type": "Select List (multiple choices)"
                }
              ]
            },
            {
              "tabName": "Schedule",
              "fields": [
                {
                  "id": "customfield_10516",
                  "name": "Change Start Date/Time",
                  "type": "Date Time Picker"
                },
                {
                  "id": "customfield_11301",
                  "name": "Change End Date/Time",
                  "type": "Date Time Picker"
                }
              ]
            },
            {
              "tabName": "Verification",
              "fields": [
                {
                  "id": "customfield_11711",
                  "name": "Verification 1",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_11712",
                  "name": "Verification 3",
                  "type": "Text Field (single line)"
                },
                {
                  "id": "customfield_11713",
                  "name": "Verification 7",
                  "type": "Text Field (single line)"
                }
              ]
            }
          ]
        }
      ],
      "shared": false,
      "projects": "WMPR"
    }
  ],
  "default_screen": "WMPR: Jira Service Desk Screen Scheme",
  "default_issuetype": "None"
}


The endpoint returns each screen used in the project, the issue type associated with the screen, the tabs within the screen and the fields within each tab. Ideally, I would want the current endpoint we have to return all the fields listed in the fields key across all the screens and tabs.
It doesn't have to return the issue types, tabs, or screens... but this concept is what I am looking for. 
