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
import processm.dbmodels.models.WorkspaceComponent
import processm.helpers.SerializableUUID
import processm.helpers.mapToArray
import processm.logging.loggedScope
import processm.logging.logger
import processm.services.JsonSerializer
import processm.services.api.models.AbstractComponent
import processm.services.api.models.LayoutCollectionMessageBody
import processm.services.api.models.OrganizationRole
import processm.services.api.models.Workspace
import processm.services.helpers.ServerSentEvent
import processm.services.helpers.eventStream
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
    val workspaceNotificationService by inject<WorkspaceNotificationService>()
    val logger = logger()

    authenticate {
        post<Paths.Workspaces> {
            val principal = call.authentication.principal<ApiUser>()!!
            val workspace = runCatching { call.receiveNullable<Workspace>() }.getOrNull()
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

            val workspace = runCatching { call.receiveNullable<Workspace>() }.getOrNull()
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
            principal.ensureUserBelongsToOrganization(component.organizationId)

            val component = workspaceService.getComponent(
                component.componentId,
                principal.userId,
                component.organizationId,
                component.workspaceId
            ).toAbstractComponent()

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
            val workspaceLayout =
                runCatching { call.receiveNullable<LayoutCollectionMessageBody>() }.getOrNull()?.data
                    ?: throw ApiException("The provided workspace data cannot be parsed")

            principal.ensureUserBelongsToOrganization(workspace.organizationId)

            val layoutData = workspaceLayout
                .mapKeys { UUID.fromString(it.key) }
                .mapValues { JsonSerializer.encodeToString(it.value) }

            workspaceService.updateLayout(
                workspace.workspaceId,
                principal.userId,
                workspace.organizationId,
                layoutData
            )

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
