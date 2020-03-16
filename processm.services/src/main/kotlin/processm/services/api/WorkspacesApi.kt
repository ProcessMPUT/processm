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
fun Route.WorkspacesApi() {

    authenticate {
        route("/workspaces") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        delete<Paths.deleteWorkspace> { _: Paths.deleteWorkspace ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.getWorkspace> { _: Paths.getWorkspace ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.getWorkspaces> { _: Paths.getWorkspaces ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        route("/workspaces/{workspaceId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }
    }
}
