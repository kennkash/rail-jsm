import com.atlassian.jira.component.ComponentAccessor
import com.atlassian.jira.issue.fields.CustomField
import com.atlassian.jira.issue.fields.config.FieldConfigScheme
import com.atlassian.jira.project.Project

def projectKey = "PLASMA"
def customFieldId = "customfield_10516"

def projectManager = ComponentAccessor.getProjectManager()
def customFieldManager = ComponentAccessor.getCustomFieldManager()

Project project = projectManager.getProjectObjByKey(projectKey)
assert project != null : "Project not found for key=${projectKey}"

CustomField cf = customFieldManager.getCustomFieldObject(customFieldId)
assert cf != null : "Custom field not found for id=${customFieldId}"

boolean isInContext(CustomField field, Project proj) {
    def schemes = field.getConfigurationSchemes() as List<FieldConfigScheme>
    if (!schemes) {
        return false
    }

    for (FieldConfigScheme scheme : schemes) {
        def associated = scheme.getAssociatedProjectObjects() as List<Project>

        // Global context: empty/null project association list
        if (!associated || associated.isEmpty()) {
            return true
        }

        // Project-scoped context
        for (Project p : associated) {
            if (p?.id == proj.id) {
                return true
            }
        }
    }

    return false
}

def applicable = isInContext(cf, project)

// --- output details to help debug why ---
def schemes = cf.getConfigurationSchemes() as List<FieldConfigScheme>

def schemeDebug = schemes.collect { FieldConfigScheme s ->
    def assoc = s.getAssociatedProjectObjects() as List<Project>
    [
        schemeId      : s?.id,
        schemeName    : s?.name,
        associatedKeys: (assoc?.collect { it?.key } ?: []),
        isGlobal      : (!assoc || assoc.isEmpty())
    ]
}

return [
    projectKey      : project.key,
    projectId       : project.id,
    customFieldId   : cf.id,
    customFieldName : cf.name,
    applicableByContext : applicable,
    schemesFound    : (schemes?.size() ?: 0),
    schemes         : schemeDebug
]
