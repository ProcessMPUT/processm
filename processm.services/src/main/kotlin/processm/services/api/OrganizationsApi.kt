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
import processm.services.api.models.*
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


        get<Paths.Organization> { organization ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(
                HttpStatusCode.OK, OrganizationMessageBody(
                    Organization(
                        organization.organizationId.toString(), false, organization.organizationId
                    )
                )
            )
        }


        get<Paths.OrganizationGroups> { organization ->
            val principal = call.authentication.principal<ApiUser>()!!

            if (!principal.organizations.containsKey(organization.organizationId)) {
                throw ApiException("The user is not a member of the organization with the provided id", HttpStatusCode.Forbidden)
            }

            val organizationGroups = organizationService.getOrganizationGroups(organization.organizationId)
                .map { Group(it.name ?: "", it.isImplicit, organization.organizationId, GroupRole.reader, it.id) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, GroupCollectionMessageBody(organizationGroups))
        }


        get<Paths.OrganizationMembers> { organizationMembers ->
            val principal = call.authentication.principal<ApiUser>()!!

            if (!principal.organizations.containsKey(organizationMembers.organizationId)) {
                throw ApiException("The user is not a member of the organization with the provided id", HttpStatusCode.Forbidden)
            }

            val members = organizationService.getOrganizationMembers(organizationMembers.organizationId)
                .map { OrganizationMember(it.user.id, it.user.email, OrganizationRole.valueOf(it.role.roleName)) }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, OrganizationMemberCollectionMessageBody(members))
        }


        get<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.OK, OrganizationCollectionMessageBody(emptyArray()))
        }


        delete<Paths.Organization> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.OrganizationMember> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }

        route("/organizations/{organizationId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
