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
import org.koin.ktor.ext.inject
import processm.services.api.models.Group
import processm.services.api.models.GroupCollectionMessageBody
import processm.services.api.models.GroupMessageBody
import processm.services.api.models.GroupRole
import processm.services.logic.GroupService

@KtorExperimentalLocationsAPI
fun Route.GroupsApi() {
    val groupService by inject<GroupService>()

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


        get<Paths.Group> { group ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userGroup = groupService.getGroup(group.groupId)

            if (!principal.organizations.containsKey(userGroup.organization.id)) {
                throw ApiException("The user is not a member of an organization containing the group with the provided id", HttpStatusCode.Forbidden)
            }

            call.respond(
                HttpStatusCode.OK, GroupMessageBody(Group(userGroup.name ?: "",userGroup.isImplicit, userGroup.organization.id, GroupRole.reader, userGroup.id))
            )
        }


        get<Paths.GroupMembers> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.Groups> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.Subgroups> { subgroups ->
            val principal = call.authentication.principal<ApiUser>()!!
            val userGroup = groupService.getGroup(subgroups.groupId)

            if (!principal.organizations.containsKey(userGroup.organization.id)) {
                throw ApiException("The user is not a member of an organization containing the group with the provided id", HttpStatusCode.Forbidden)
            }

            val groups = groupService.getSubgroups(userGroup.id)
                .map { Group(it.name ?: "", it.isImplicit, it.organization.id,GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, GroupCollectionMessageBody(groups))
        }


        delete<Paths.Group> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.GroupMember> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.Subgroup> { _ ->
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
