package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.locations.put
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import processm.helpers.mapToArray
import processm.helpers.toUUID
import processm.logging.loggedScope
import processm.services.api.models.Group
import processm.services.api.models.Organization
import processm.services.api.models.OrganizationRole
import processm.services.api.models.UserInfo
import processm.services.logic.*
import processm.services.respondCreated
import java.util.*

fun Route.GroupsApi() = loggedScope { logger ->
    val groupService by inject<GroupService>()
    val organizationService by inject<OrganizationService>()
    val accountService by inject<AccountService>()

    fun getOrganizationRelatedToGroup(groupId: UUID): Organization {
        val rootGroupId = groupService.getRootGroupId(groupId)

        return organizationService.getOrganizationBySharedGroupId(rootGroupId).toApi()
    }

    authenticate {
        get<Paths.Groups> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId)

            val groups = organizationService.getOrganizationGroups(path.organizationId)

            call.respond(HttpStatusCode.OK, groups.mapToArray { it.toApi() })
        }

        post<Paths.Groups> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            val newGroup =
                kotlin.runCatching { call.receiveNullable<Group>() }.getOrNull().validateNotNull { "Invalid group." }
            if (path.organizationId != newGroup.organizationId) {
                logger.warn("path.organizationId '${path.organizationId}' does not equal newGroup.organizationId '${newGroup.organizationId}'; ignoring the latter.")
            }
            val group = groupService.create(
                name = newGroup.name,
                organizationId = path.organizationId
            )

            call.respondCreated(Paths.Group(path.organizationId, group.id.value))
        }

        get<Paths.Group> { group ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(group.groupId)

            principal.ensureUserBelongsToOrganization(organization.id!!)

            val userGroup = groupService.getGroup(group.groupId)

            call.respond(
                HttpStatusCode.OK,
                userGroup.toApi()
            )
        }

        put<Paths.Group> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            val newGroup = call.receive<ApiGroup>()

            groupService.update(path.groupId) {
                this.name = newGroup.name
                // so far we do not have use case and do not support changing of other attributes of a group
            }

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.Group> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            groupService.remove(path.groupId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.GroupMembers> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.reader)

            val members = groupService.getGroup(path.groupId).members
            val organization = organizationService.get(path.organizationId)

            call.respond(HttpStatusCode.OK, members.mapToArray {
                UserInfo(
                    id = it.id.value,
                    userEmail = it.email,
                    username = it.email, // TODO: drop username or use firstName and lastName
                    organization = organization.name,
                    organizationRoles = accountService.getRolesAssignedToUser(it.id.value).map {
                        it.organization.id.value.toString() to it.role.toApi()
                    }.toMap()
                )
            })
        }

        post<Paths.GroupMembers> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            val newMemberId =
                kotlin.runCatching { call.receiveNullable<String>() }.getOrNull().validateNotNull { "Invalid user id." }
                    .toUUID()!!
            groupService.attachUserToGroup(newMemberId, path.groupId)

            call.respondCreated(Paths.GroupMember(path.organizationId, path.groupId, newMemberId))
        }

        delete<Paths.GroupMember> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            groupService.detachUserFromGroup(path.userId, path.groupId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.Subgroups> { subgroups ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(subgroups.groupId)

            principal.ensureUserBelongsToOrganization(organization.id!!)

            val groups = groupService.getSubgroups(subgroups.groupId).mapToArray { it.toApi() }

            call.respond(HttpStatusCode.OK, groups)
        }

        post<Paths.Subgroups> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.Subgroup> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
