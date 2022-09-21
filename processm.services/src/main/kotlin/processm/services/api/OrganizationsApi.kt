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
import processm.services.api.models.Group
import processm.services.api.models.GroupRole
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.OrganizationService
import processm.services.respondCreated

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {
    val organizationService by inject<OrganizationService>()

    authenticate {

        post<Paths.OrganizationsOrganizationIdMembers> { organization ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(organization.organizationId, OrganizationRole.owner)
            val member = call.receive<OrganizationMember>()

            val user = organizationService.addMember(
                organization.organizationId,
                member.email,
                member.organizationRole
            )

            call.respondCreated(Paths.OrganizationOrgIdMembersUserId(organization.organizationId, user.id.value), null)
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


        get<Paths.OrganizationGroups> { organization ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(organization.organizationId)

            val organizationGroups = organizationService.getOrganizationGroups(organization.organizationId)
                .map { Group(it.name ?: "", it.isImplicit, organization.organizationId, GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, organizationGroups)
        }


        get<Paths.OrganizationsOrganizationIdMembers> { organizationMembers ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(organizationMembers.organizationId)

            val members = organizationService.getMember(organizationMembers.organizationId)
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


        get<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.Organization> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.OrganizationOrgIdMembersUserId> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        put<Paths.Organization> {
            val principal = call.authentication.principal<ApiUser>()
            // TODO
            call.respond(HttpStatusCode.NotImplemented)
        }
    }
}
