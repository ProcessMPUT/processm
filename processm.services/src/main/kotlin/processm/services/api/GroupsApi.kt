package processm.services.api

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.auth.authentication
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route
import org.koin.ext.getOrCreateScope
import org.koin.ktor.ext.inject
import processm.services.api.models.*
import processm.services.logic.GroupService
import processm.services.logic.OrganizationService
import processm.services.models.OrganizationDto
import java.util.*

@KtorExperimentalLocationsAPI
fun Route.GroupsApi() {
    val groupService by inject<GroupService>()
    val organizationService by inject<OrganizationService>()

    fun getOrganizationRelatedToGroup(groupId: UUID): OrganizationDto {
        val rootGroupId = groupService.getRootGroupId(groupId)

        return organizationService.getOrganizationBySharedGroupId(rootGroupId)
    }

    authenticate {
        route("/groups/{groupId}/members") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route("/groups") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route("/groups/{groupId}/subgroups") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        get<Paths.getGroup> { group: Paths.getGroup ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userGroup = groupService.getGroup(group.groupId)
            val organization = getOrganizationRelatedToGroup(group.groupId)

            if (!principal.organizations.containsKey(organization.id)) {
                throw ApiException("User is not member of organization containing group with provided id", HttpStatusCode.Forbidden)
            }

            call.respond(
                HttpStatusCode.OK, GroupMessageBody(Group(userGroup.name ?: "", userGroup.isImplicit, UUID.randomUUID(), GroupRole.reader, userGroup.id))
            )
        }

        get<Paths.getGroupMembers> { _: Paths.getGroupMembers ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        get<Paths.getGroups> { _: Paths.getGroups ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        get<Paths.getSubgroups> { group: Paths.getSubgroups ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(group.groupId)

            if (!principal.organizations.containsKey(organization.id)) {
                throw ApiException("User is not member of organization containing group with provided id", HttpStatusCode.Forbidden)
            }

            val subgroups = groupService.getSubgroups(group.groupId)
                .map { Group(it.name ?: "", it.isImplicit, UUID.randomUUID(), GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, GroupCollectionMessageBody(subgroups))
        }

        delete<Paths.removeGroup> { _: Paths.removeGroup ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        delete<Paths.removeGroupMember> { _: Paths.removeGroupMember ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        delete<Paths.removeSubgroup> { _: Paths.removeSubgroup ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        route("/groups/{groupId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
