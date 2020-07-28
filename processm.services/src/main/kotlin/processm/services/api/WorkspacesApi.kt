package processm.services.api

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.request.receiveOrNull
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import org.koin.ktor.ext.inject
import processm.core.helpers.mapToArray
import processm.services.api.models.*
import processm.services.logic.WorkspaceService
import processm.services.models.CausalNetDto
import java.util.*

@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    val workspaceService by inject<WorkspaceService>()

    authenticate {
        route("/organizations/{organizationId}/workspaces") {
            post {
                val principal = call.authentication.principal<ApiUser>()!!
                val workspace = call.receiveOrNull<WorkspaceMessageBody>()?.data
                val organizationId = try {
                    UUID.fromString(call.parameters["organizationId"])
                } catch (exception: IllegalArgumentException) {
                    throw ApiException("'organizationId' parameter needs to be specified when creating new workspace", HttpStatusCode.BadRequest)
                }

                principal.ensureUserBelongsToOrganization(organizationId)

                if (workspace == null || workspace.name.isEmpty()) {
                    throw ApiException("Workspace name needs to be specified when creating new workspace", HttpStatusCode.BadRequest)
                }

                val workspaceId = workspaceService.createWorkspace(workspace.name, principal.userId, organizationId)

                call.respond(HttpStatusCode.Created, WorkspaceMessageBody(Workspace(workspace.name, workspaceId)))
            }
        }

        delete<Paths.Workspace> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(workspace.organizationId, OrganizationRole.writer)

            val removedWorkspacesCount = workspaceService.removeWorkspace(workspace.workspaceId, principal.userId, workspace.organizationId)

            call.respond(if (removedWorkspacesCount > 0) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        get<Paths.Workspaces> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaces = workspaceService.getUserWorkspaces(principal.userId, workspace.organizationId)
                    .map { Workspace(it.name, it.id) }.toTypedArray()

            call.respond(HttpStatusCode.OK, WorkspaceCollectionMessageBody(workspaces))
        }


        route("/organizations/{organizationId}/workspaces/{workspaceId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        get<Paths.WorkspaceComponent> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponentData> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponents> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val components = workspaceService.getWorkspaceComponents(workspace.workspaceId)
                .mapToArray {
                    val data = it.data as CausalNetDto
                    ComponentAbstract(it.name, ComponentType.causalNet, it.id, CausalNetComponentData(
                        data.nodes.mapToArray { CausalNetComponentDataAllOfNodes(it.splits, it.joins, it.id) },
                        data.edges.mapToArray { CausalNetComponentDataAllOfEdges(it.sourceNodeId, it.targetNodeId) }
                    ))
                }

            call.respond(HttpStatusCode.OK, ComponentCollectionMessageBody(components))
        }
    }
}
