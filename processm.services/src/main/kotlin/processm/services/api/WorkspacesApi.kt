package processm.services.api

import com.google.gson.Gson
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import org.koin.ktor.ext.inject
import processm.core.logging.loggedScope
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.WorkspaceDto
import processm.services.api.models.*
import processm.services.logic.WorkspaceService
import java.util.*

@Suppress("FunctionName")
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

            val workspaceRemoved =
                workspaceService.removeWorkspace(workspace.workspaceId, principal.userId, workspace.organizationId)

            call.respond(if (workspaceRemoved) HttpStatusCode.NoContent else HttpStatusCode.NotFound)
        }

        get<Paths.Workspaces> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaces = workspaceService.getUserWorkspaces(principal.userId, workspace.organizationId)
                .map { Workspace(it.name, it.id) }.toTypedArray()

            call.respond(HttpStatusCode.OK, WorkspaceCollectionMessageBody(workspaces))
        }

        put<Paths.Workspace> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId)

            val workspace = call.receiveOrNull<WorkspaceMessageBody>()?.data
                ?: throw ApiException("The provided workspace data cannot be parsed")

            workspaceService.updateWorkspace(
                principal.userId,
                path.organizationId,
                WorkspaceDto(path.workspaceId, workspace.name)
            )

            call.respond(HttpStatusCode.OK)
        }

        get<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            call.respond(HttpStatusCode.NotImplemented)
        }

        put<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceComponent = call.receiveOrNull<ComponentMessageBody>()?.data
                ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(component.organizationId)
            workspaceComponent.apply {
                workspaceService.addOrUpdateWorkspaceComponent(
                    component.componentId,
                    component.workspaceId,
                    principal.userId,
                    component.organizationId,
                    name,
                    query,
                    dataStore,
                    ComponentTypeDto.byTypeNameInDatabase(type.toString()),
                    // TODO: replace the dependency on Gson with kotlinx/serialization
                    workspaceComponent.customizationData?.let { Gson().toJson(it) },
                    workspaceComponent.layout?.let { Gson().toJson(it) }
                )
            }

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(component.organizationId)
            workspaceService.removeWorkspaceComponent(
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
            loggedScope { logger ->
                val principal = call.authentication.principal<ApiUser>()!!

                principal.ensureUserBelongsToOrganization(workspace.organizationId)

                val components = workspaceService.getWorkspaceComponents(
                    workspace.workspaceId,
                    principal.userId,
                    workspace.organizationId
                ).mapNotNull {
                    try {
                        it.toAbstractComponent()
                    } catch (e: Throwable) {
                        logger.warn("Failed to fetch component ${it.id.value}.", e)
                        null
                    }
                }.toTypedArray()

                call.respond(HttpStatusCode.OK, ComponentCollectionMessageBody(components))
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

            workspaceService.updateWorkspaceLayout(
                workspace.workspaceId,
                principal.userId,
                workspace.organizationId,
                layoutData
            )

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
