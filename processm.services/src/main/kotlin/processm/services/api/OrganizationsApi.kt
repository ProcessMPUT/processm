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

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {

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


        get<Paths.getOrganization> { _: Paths.getOrganization ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.getOrganizationMembers> { _: Paths.getOrganizationMembers ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.getOrganizations> { _: Paths.getOrganizations ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.removeOrganization> { _: Paths.removeOrganization ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        delete<Paths.removeOrganizationMember> { _: Paths.removeOrganizationMember ->
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
