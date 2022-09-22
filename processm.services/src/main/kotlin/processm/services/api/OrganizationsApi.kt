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
import org.koin.ktor.ext.inject
import processm.services.api.models.Group
import processm.services.api.models.GroupRole
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.OrganizationService
import processm.services.logic.Reason
import processm.services.logic.validateNot
import processm.services.respondCreated

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {
    val organizationService by inject<OrganizationService>()

    authenticate {
        // region Organizations
        get<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }

        post<Paths.Organizations> {
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }

        get<Paths.Organization> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }

        delete<Paths.Organization> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }

        put<Paths.Organization> {
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }
        // end region

        // region Organization members
        get<Paths.OrganizationsOrgIdMembers> { params ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(params.organizationId)

            val members = organizationService.getMember(params.organizationId)
                .map {
                    OrganizationMember(
                        id = it.user.id,
                        email = it.user.email,
                        organizationRole = it.role
                    )
                }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, members)
        }

        post<Paths.OrganizationsOrgIdMembers> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)
            val member = call.receive<OrganizationMember>()

            val user = organizationService.addMember(
                params.organizationId,
                requireNotNull(member.email),
                member.organizationRole
            )

            call.respondCreated(Paths.OrganizationsOrgIdMembersUserId(params.organizationId, user.id.value))
        }

        patch<Paths.OrganizationsOrgIdMembersUserId> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)
            val member = call.receive<OrganizationMember>()

            params.userId.validateNot(
                principal.userId,
                Reason.UnprocessableResource,
                "Cannot change role of the current user."
            )
            organizationService.updateMember(params.organizationId, params.userId, member.organizationRole)

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.OrganizationsOrgIdMembersUserId> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)

            params.userId.validateNot(principal.userId, Reason.UnprocessableResource, "Cannot delete the current user.")
            organizationService.removeMember(params.organizationId, params.userId)

            call.respond(HttpStatusCode.NoContent)
        }
        // endregion

        // region Groups
        get<Paths.OrganizationGroups> { organization ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(organization.organizationId)

            val organizationGroups = organizationService.getOrganizationGroups(organization.organizationId)
                .map { Group(it.name ?: "", it.isImplicit, organization.organizationId, GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, organizationGroups)
        }
        // endregion
    }
}
