package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.locations.patch
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.koin.ktor.ext.inject
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.RoleType
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.Workspaces
import processm.helpers.SerializableUUID
import processm.helpers.mapToArray
import processm.logging.loggedScope
import processm.logging.logger
import processm.services.JsonSerializer
import processm.services.api.models.*
import processm.services.helpers.ServerSentEvent
import processm.services.helpers.eventStream
import processm.services.logic.ACLService
import processm.services.logic.WorkspaceNotificationService
import processm.services.logic.WorkspaceService
import java.util.*


@ServerSentEvent("update")
@Serializable
data class ComponentUpdateEventPayload(val componentId: SerializableUUID)

@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("FunctionName")
@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    val workspaceService by inject<WorkspaceService>()
    val aclService by inject<ACLService>()
    val workspaceNotificationService by inject<WorkspaceNotificationService>()
    val logger = logger()

    authenticate {
        post<Paths.Workspaces> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            val newWorkspace = runCatching { call.receiveNullable<NewWorkspace>() }.getOrNull()
                ?: throw ApiException("The provided workspace data cannot be parsed")

            // The user must be a member of the organization, but does not require any privileges, as the privileges are related only to user and group management
            principal.ensureUserBelongsToOrganization(newWorkspace.organizationId, OrganizationRole.none)

            if (newWorkspace.name.isEmpty()) {
                throw ApiException("Workspace name needs to be specified when creating new workspace")
            }

            val workspaceId = workspaceService.create(newWorkspace.name, principal.userId, newWorkspace.organizationId)

            call.respond(HttpStatusCode.Created, Workspace(newWorkspace.name, workspaceId))
        }

        delete<Paths.Workspace> { path ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, path.workspaceId, RoleType.Owner)
            workspaceService.remove(path.workspaceId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.Workspaces> {
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaces = workspaceService.getUserWorkspaces(principal.userId)
                .map { Workspace(it.name, it.id.value) }.toTypedArray()

            call.respond(HttpStatusCode.OK, workspaces)
        }

        put<Paths.Workspace> { path ->
            val principal = call.authentication.principal<ApiUser>()!!

            val workspace = runCatching { call.receiveNullable<Workspace>() }.getOrNull()
                ?: throw ApiException("The provided workspace data cannot be parsed")

            aclService.checkAccess(principal.userId, Workspaces, path.workspaceId, RoleType.Writer)
            workspaceService.update(path.workspaceId, workspace.name)

            call.respond(HttpStatusCode.OK)
        }

        get<Paths.WorkspaceComponent> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            aclService.checkAccess(principal.userId, Workspaces, path.workspaceId, RoleType.Reader)

            val component = workspaceService.getComponent(path.componentId).toAbstractComponent()

            call.respond(HttpStatusCode.OK, component)
        }

        put<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceComponent = runCatching { call.receiveNullable<AbstractComponent>() }.let {
                it.getOrThrow() ?: throw ApiException(
                    publicMessage = "The provided workspace data cannot be parsed",
                    message = it.exceptionOrNull()!!.message
                )
            }

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Writer)
            with(workspaceComponent) {
                workspaceService.addOrUpdateComponent(
                    component.componentId,
                    component.workspaceId,
                    name,
                    query,
                    dataStore,
                    ComponentTypeDto.byTypeNameInDatabase(type.toString()),
                    customizationData = customizationData?.let { JsonSerializer.encodeToString(it) },
                    layoutData = layout?.let { JsonSerializer.encodeToString(it) },
                    data = data?.let { JsonSerializer.encodeToString(it) },
                    customProperties = customProperties
                )
            }

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Writer)
            workspaceService.removeComponent(component.componentId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.WorkspaceComponentData> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.WorkspaceComponents> { path ->
            loggedScope {
                val principal = call.authentication.principal<ApiUser>()!!

                aclService.checkAccess(principal.userId, Workspaces, path.workspaceId, RoleType.Reader)

                val components = workspaceService.getComponents(path.workspaceId)
                    .mapToArray(WorkspaceComponent::toAbstractComponent)

                call.respond(HttpStatusCode.OK, components)
            }
        }

        patch<Paths.WorkspaceLayout> { workspace ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceLayout =
                runCatching { call.receiveNullable<LayoutCollectionMessageBody>() }.getOrNull()?.data
                    ?: throw ApiException("The provided workspace data cannot be parsed")

            aclService.checkAccess(principal.userId, Workspaces, workspace.workspaceId, RoleType.Reader)

            val layoutData = workspaceLayout
                .mapKeys { UUID.fromString(it.key) }
                .mapValues { JsonSerializer.encodeToString(it.value) }

            workspaceService.updateLayout(layoutData)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.Workspace> { workspace ->
            val channel = Channel<UUID>(Channel.CONFLATED)
            try {
                workspaceNotificationService.subscribe(workspace.workspaceId, channel)
                call.eventStream {
                    while (!channel.isClosedForReceive) {
                        val componentId = channel.receive()
                        writeEvent(ComponentUpdateEventPayload(componentId))
                    }
                }
            } finally {
                workspaceNotificationService.unsubscribe(workspace.workspaceId, channel)
                channel.close()
            }
        }
    }
}
