package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
import processm.services.api.models.Group
import processm.services.api.models.GroupRole
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.OrganizationService

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {
    val organizationService by inject<OrganizationService>()

    authenticate {
        route("/organizations/{organizationId}/members") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }

        route("/organizations") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
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


        get<Paths.OrganizationMembers> { organizationMembers ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(organizationMembers.organizationId)

            val members = organizationService.getOrganizationMembers(organizationMembers.organizationId)
                .map { OrganizationMember(it.user.id, it.user.email, OrganizationRole.valueOf(it.role.roleName)) }
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


        delete<Paths.OrganizationMember> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        route("/organizations/{organizationId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()
                // TODO
                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
