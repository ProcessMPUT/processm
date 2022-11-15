package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import processm.services.api.models.Group
import processm.services.api.models.Organization
import processm.services.logic.GroupService
import processm.services.logic.OrganizationService
import processm.services.logic.toApi
import java.util.*

fun Route.GroupsApi() {
    val groupService by inject<GroupService>()
    val organizationService by inject<OrganizationService>()

    fun getOrganizationRelatedToGroup(groupId: UUID): Organization {
        val rootGroupId = groupService.getRootGroupId(groupId)

        return organizationService.getOrganizationBySharedGroupId(rootGroupId).toApi()
    }

    authenticate {
        route("/groups/{groupId}/members") {
            post {
                call.authentication.principal<ApiUser>()
                // TODO
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route("/groups") {
            post {
                val principal = call.authentication.principal<ApiUser>()
                // TODO
                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route("/groups/{groupId}/subgroups") {
            post {
                val principal = call.authentication.principal<ApiUser>()
                // TODO
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.Group> { group ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(group.groupId)

            principal.ensureUserBelongsToOrganization(organization.id!!)

            val userGroup = groupService.getGroup(group.groupId)

            call.respond(
                HttpStatusCode.OK,
                Group(
                    userGroup.name ?: "",
                    userGroup.isImplicit,
                    organization.id,
                    userGroup.id.value
                )
            )
        }


        get<Paths.GroupMembers> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.Groups> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.Subgroups> { subgroups ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(subgroups.groupId)

            principal.ensureUserBelongsToOrganization(organization.id!!)

            val groups = groupService.getSubgroups(subgroups.groupId)
                .map { Group(it.name ?: "", it.isImplicit, organization.id, it.id.value) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, groups)
        }


        delete<Paths.Group> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.GroupMember> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.Subgroup> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }

        route("/groups/{groupId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()
                // TODO
                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
