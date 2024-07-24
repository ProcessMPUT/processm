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
import kotlinx.serialization.encodeToString
import org.koin.ktor.ext.inject
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.RoleType
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.Workspaces
import processm.helpers.mapToArray
import processm.logging.loggedScope
import processm.logging.logger
import processm.services.JsonSerializer
import processm.services.api.models.*
import processm.services.helpers.ExceptionReason
import processm.services.logic.ACLService
import processm.services.logic.WorkspaceService
import java.util.*


@OptIn(ExperimentalCoroutinesApi::class)
@Suppress("FunctionName")
@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    val workspaceService by inject<WorkspaceService>()
    val aclService by inject<ACLService>()

    authenticate {
        post<Paths.Workspaces> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            val newWorkspace = runCatching { call.receiveNullable<NewWorkspace>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UnparsableData)

            newWorkspace.organizationId?.let { organizationId ->
                // The user must be a member of the organization, but does not require any privileges, as the privileges are related only to user and group management
                principal.ensureUserBelongsToOrganization(organizationId, OrganizationRole.none)
            }

            if (newWorkspace.name.isEmpty()) {
                throw ApiException(ExceptionReason.WorkspaceNameRequired)
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
                ?: throw ApiException(ExceptionReason.UnparsableData)

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

        get<Paths.EmptyComponent> { path ->
            val principal = call.authentication.principal<ApiUser>()!!

            val component = workspaceService.getEmptyComponent(ComponentTypeDto.byTypeNameInDatabase(path.type))

            call.respond(HttpStatusCode.OK, component)
        }

        put<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!
            val workspaceComponent = runCatching { call.receiveNullable<AbstractComponent>() }.let {
                it.getOrThrow() ?: throw ApiException(
                    ExceptionReason.UnparsableData,
                    message = it.exceptionOrNull()!!.message
                )
            }

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Writer)
            with(workspaceComponent) {
                val actualComponent = workspaceService.addOrUpdateComponent(
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

                call.respond(HttpStatusCode.OK, actualComponent.componentLastModified)
            }
        }

        delete<Paths.WorkspaceComponent> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Writer)
            workspaceService.removeComponent(component.componentId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.WorkspaceComponentData> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Reader)

            call.respond(HttpStatusCode.OK, workspaceService.getAvailableVersions(component.componentId))
        }

        get<Paths.WorkspaceComponentDataVariant> { component ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, component.workspaceId, RoleType.Reader)

            val data = workspaceService.getDataVariant(component.componentId, component.variantId)

            if (data !== null) call.respond(HttpStatusCode.OK, data)
            else call.respond(HttpStatusCode.NotFound)
        }

        patch<Paths.WorkspaceComponentData> { data ->
            val principal = call.authentication.principal<ApiUser>()!!

            aclService.checkAccess(principal.userId, Workspaces, data.workspaceId, RoleType.Writer)
            val modelVersion = runCatching { call.receiveNullable<Long>() }.let {
                it.getOrThrow() ?: throw ApiException(
                    ExceptionReason.UnparsableData,
                    message = it.exceptionOrNull()!!.message
                )
            }
            workspaceService.acceptModel(data.componentId, modelVersion)
            call.respond(HttpStatusCode.NoContent)
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
                    ?: throw ApiException(ExceptionReason.UnparsableData)

            aclService.checkAccess(principal.userId, Workspaces, workspace.workspaceId, RoleType.Reader)

            val layoutData = workspaceLayout
                .mapKeys { UUID.fromString(it.key) }
                .mapValues { JsonSerializer.encodeToString(it.value) }

            workspaceService.updateLayout(layoutData)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
