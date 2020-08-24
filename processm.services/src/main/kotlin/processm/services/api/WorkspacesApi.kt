package processm.services.api

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.locations.*
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import kotlinx.serialization.ImplicitReflectionSerializer
import org.koin.ktor.ext.inject
import processm.core.helpers.mapToArray
import processm.services.api.models.*
import processm.services.logic.WorkspaceService
import processm.services.models.CausalNetDto
import processm.services.models.ComponentTypeDto

@UseExperimental(ImplicitReflectionSerializer::class)
@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    val workspaceService by inject<WorkspaceService>()

    authenticate {
        post<Paths.Workspaces> {
            val principal = call.authentication.principal<ApiUser>()!!
            val workspace = call.receiveOrNull<WorkspaceMessageBody>()?.data
                ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(it.organizationId, OrganizationRole.writer)

            if (workspace.name.isEmpty()) {
                throw ApiException("Workspace name needs to be specified when creating new workspace")
            }

            val workspaceId = workspaceService.createWorkspace(workspace.name, principal.userId, it.organizationId)

            call.respond(HttpStatusCode.Created, WorkspaceMessageBody(Workspace(workspace.name, workspaceId)))
        }

        delete<Paths.Workspace> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(workspace.organizationId, OrganizationRole.writer)

            val workspaceRemoved = workspaceService.removeWorkspace(workspace.workspaceId, principal.userId, workspace.organizationId)

            call.respond(if (workspaceRemoved) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        get<Paths.Workspaces> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaces = workspaceService.getUserWorkspaces(principal.userId, workspace.organizationId)
                    .map { Workspace(it.name, it.id) }.toTypedArray()

            call.respond(HttpStatusCode.OK, WorkspaceCollectionMessageBody(workspaces))
        }

        put<Paths.Workspace> {
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponent> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        put<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceComponent = call.receiveOrNull<ComponentMessageBody>()?.data
                                         ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(component.organizationId)
            workspaceComponent.apply {
                workspaceService.updateWorkspaceComponent(component.componentId, component.workspaceId, principal.userId, component.organizationId, name, ComponentTypeDto.byTypeNameInDatabase(type.toString()), if (workspaceComponent.customizationData != null) Gson().toJson(
                    workspaceComponent.customizationData
                ) else null)
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.WorkspaceComponentData> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponents> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(workspace.organizationId)

            val components = workspaceService.getWorkspaceComponents(workspace.workspaceId, principal.userId, workspace.organizationId)
                .mapToArray {
                    val customizationData = if (!it.customizationData.isNullOrEmpty())
                        Gson().fromJson(it.customizationData, CausalNetComponentAllOfCustomizationData::class.java)
                        else null
                    val data = it.data as CausalNetDto
                    AbstractComponent(it.id, it.name,  ComponentType.causalNet, CausalNetComponentData(
                        data.nodes.mapToArray { CausalNetComponentDataAllOfNodes(it.id, it.splits, it.joins) },
                        data.edges.mapToArray { CausalNetComponentDataAllOfEdges(it.sourceNodeId, it.targetNodeId) }
                    ), customizationData)
                }

            call.respond(HttpStatusCode.OK, ComponentCollectionMessageBody(components))
        }
    }
}
