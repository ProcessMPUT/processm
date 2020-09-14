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
import processm.dbmodels.models.OrganizationDto
import processm.services.api.models.Group
import processm.services.api.models.GroupCollectionMessageBody
import processm.services.api.models.GroupMessageBody
import processm.services.api.models.GroupRole
import processm.services.logic.GroupService
import processm.services.logic.OrganizationService
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
                call.authentication.principal<ApiUser>()

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
            val organization = getOrganizationRelatedToGroup(group.groupId)

            principal.ensureUserBelongsToOrganization(organization.id)

            val userGroup = groupService.getGroup(group.groupId)

            call.respond(
                HttpStatusCode.OK,
                GroupMessageBody(
                    Group(
                        userGroup.name ?: "",
                        userGroup.isImplicit,
                        organization.id,
                        GroupRole.reader,
                        userGroup.id
                    )
                )
            )
        }


        get<Paths.GroupMembers> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.Groups> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.Subgroups> { subgroups ->
            val principal = call.authentication.principal<ApiUser>()!!
            val organization = getOrganizationRelatedToGroup(subgroups.groupId)

            principal.ensureUserBelongsToOrganization(organization.id)

            val groups = groupService.getSubgroups(subgroups.groupId)
                .map { Group(it.name ?: "", it.isImplicit, organization.id, GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, GroupCollectionMessageBody(groups))
        }


        delete<Paths.Group> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
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
