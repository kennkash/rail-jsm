2026-02-26T17:58:09,300 WARN [runner.ScriptBindingsManager]: Group: Confluence
2026-02-26T17:58:09,301 WARN [runner.ScriptBindingsManager]:   ID: 49
2026-02-26T17:58:09,301 WARN [runner.ScriptBindingsManager]:   Order Optional: Optional[0]
2026-02-26T17:58:09,301 WARN [runner.ScriptBindingsManager]:   Order Present?: true
2026-02-26T17:58:09,301 WARN [runner.ScriptBindingsManager]:   Order Value: 0
2026-02-26T17:58:09,301 WARN [runner.ScriptBindingsManager]: ------------------------------------
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]: Group: Jira
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   ID: 50
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Optional: Optional[1]
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Present?: true
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Value: 1
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]: ------------------------------------
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]: Group: Development
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   ID: 417
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Optional: Optional[3]
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Present?: true
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]:   Order Value: 3
2026-02-26T17:58:09,302 WARN [runner.ScriptBindingsManager]: ------------------------------------

import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.servicedesk.api.ServiceDeskManager
import com.atlassian.servicedesk.api.requesttype.RequestTypeService

def projectKey = "YOUR_PROJECT_KEY"

def project = ComponentAccessor.projectManager.getProjectObjByKey(projectKey)
def sdManager = ComponentAccessor.getOSGiComponentInstanceOfType(ServiceDeskManager)
def rtService = ComponentAccessor.getOSGiComponentInstanceOfType(RequestTypeService)

def serviceDesk = sdManager.getServiceDeskForProject(project)
def user = ComponentAccessor.jiraAuthenticationContext.loggedInUser

def query = rtService.newQueryBuilder()
        .serviceDesk(serviceDesk.id)
        .requestOverrideSecurity(true)
        .filterHidden(true)
        .build()

def results = rtService.getRequestTypes(user, query).results

def seen = [] as Set

results.each { rt ->
    rt.groups?.each { group ->
        if (!seen.contains(group.id)) {
            seen << group.id
            println "Group: ${group.name}"
            println "  ID: ${group.id}"
            println "  Order Optional: ${group.order}"
            println "  Order Present?: ${group.order?.present}"
            println "  Order Value: ${group.order?.orElse(null)}"
            println "------------------------------------"
        }
    }
}
