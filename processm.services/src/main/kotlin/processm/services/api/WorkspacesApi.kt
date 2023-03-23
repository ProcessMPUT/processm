package processm.services.api

import com.google.gson.Gson
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject
import processm.core.helpers.mapToArray
import processm.core.logging.loggedScope
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceComponent
import processm.services.api.models.AbstractComponent
import processm.services.api.models.LayoutCollectionMessageBody
import processm.services.api.models.OrganizationRole
import processm.services.api.models.Workspace
import processm.services.logic.WorkspaceService
import java.util.*

@Suppress("FunctionName")
@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    val workspaceService by inject<WorkspaceService>()

    authenticate {
        post<Paths.Workspaces> {
            val principal = call.authentication.principal<ApiUser>()!!
            val workspace = call.receiveOrNull<Workspace>()
                ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(it.organizationId, OrganizationRole.writer)

            if (workspace.name.isEmpty()) {
                throw ApiException("Workspace name needs to be specified when creating new workspace")
            }

            val workspaceId = workspaceService.create(workspace.name, principal.userId, it.organizationId)

            call.respond(HttpStatusCode.Created, Workspace(workspace.name, workspaceId))
        }

        delete<Paths.Workspace> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(workspace.organizationId, OrganizationRole.writer)

            workspaceService.remove(workspace.workspaceId, principal.userId, workspace.organizationId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.Workspaces> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaces = workspaceService.getUserWorkspaces(principal.userId, workspace.organizationId)
                .map { Workspace(it.name, it.id.value) }.toTypedArray()

            call.respond(HttpStatusCode.OK, workspaces)
        }

        put<Paths.Workspace> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId)

            val workspace = call.receiveOrNull<Workspace>()
                ?: throw ApiException("The provided workspace data cannot be parsed")

            workspaceService.update(
                principal.userId,
                path.organizationId,
                workspace
            )

            call.respond(HttpStatusCode.OK)
        }

        get<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            call.respond(HttpStatusCode.NotImplemented)
        }

        put<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceComponent = call.receiveOrNull<AbstractComponent>()
                ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(component.organizationId)
            with(workspaceComponent) {
                workspaceService.addOrUpdateComponent(
                    component.componentId,
                    component.workspaceId,
                    principal.userId,
                    component.organizationId,
                    name,
                    query,
                    dataStore,
                    ComponentTypeDto.byTypeNameInDatabase(type.toString()),
                    // TODO: replace the dependency on Gson with kotlinx/serialization
                    customizationData = customizationData?.let { Gson().toJson(it) },
                    layoutData = layout?.let { Gson().toJson(it) },
                    data = data?.let { Gson().toJson(it) }
                )
            }

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(component.organizationId)
            workspaceService.removeComponent(
                component.componentId,
                component.workspaceId,
                principal.userId,
                component.organizationId
            )

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.WorkspaceComponentData> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponents> { workspace ->
            loggedScope {
                val principal = call.authentication.principal<ApiUser>()!!

                principal.ensureUserBelongsToOrganization(workspace.organizationId)

                val components = workspaceService.getComponents(
                    workspace.workspaceId,
                    principal.userId,
                    workspace.organizationId
                ).mapToArray(WorkspaceComponent::toAbstractComponent)

                call.respond(HttpStatusCode.OK, components)
            }
        }

        patch<Paths.WorkspaceLayout> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceLayout = call.receiveOrNull<LayoutCollectionMessageBody>()?.data
                ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(workspace.organizationId)

            val layoutData = workspaceLayout
                .mapKeys { UUID.fromString(it.key) }
                .mapValues { Gson().toJson(it.value) }

            workspaceService.updateLayout(
                workspace.workspaceId,
                principal.userId,
                workspace.organizationId,
                layoutData
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
