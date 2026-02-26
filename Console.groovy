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
