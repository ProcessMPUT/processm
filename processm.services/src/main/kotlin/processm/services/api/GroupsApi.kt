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
fun Route.GroupsApi() {

    authenticate {
        route("/groups/{groupId}/members") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        route("/groups") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        route("/groups/{groupId}/subgroups") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        get<Paths.getGroup> { _: Paths.getGroup ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.getGroupMembers> { _: Paths.getGroupMembers ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.getGroups> { _: Paths.getGroups ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        get<Paths.getSubgroups> { _: Paths.getSubgroups ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.removeGroup> { _: Paths.removeGroup ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.removeGroupMember> { _: Paths.removeGroupMember ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.removeSubgroup> { _: Paths.removeSubgroup ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        route("/groups/{groupId}") {
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
