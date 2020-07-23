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
import processm.services.api.models.Workspace
import processm.services.api.models.WorkspaceCollectionMessageBody
import processm.services.api.models.WorkspaceMessageBody

@KtorExperimentalLocationsAPI
fun Route.WorkspacesApi() {
    authenticate {
        route("/workspaces") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.Workspace> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.NotImplemented)
        }


        get<Paths.Workspace> { workspace ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(
                HttpStatusCode.OK,
                WorkspaceMessageBody(Workspace(workspace.workspaceId.toString(), workspace.workspaceId))
            )
        }


        get<Paths.Workspaces> { _ ->
            val principal = call.authentication.principal<ApiUser>()

            call.respond(HttpStatusCode.OK, WorkspaceCollectionMessageBody(emptyArray()))
        }


        route("/workspaces/{workspaceId}") {
            put {
                val principal = call.authentication.principal<ApiUser>()

                call.respond(HttpStatusCode.NotImplemented)
            }
        }
    }
}
