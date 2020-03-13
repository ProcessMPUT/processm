package processm.services.api

import com.google.gson.Gson
import io.ktor.application.call
import io.ktor.auth.authentication
import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.delete
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Route
import io.ktor.routing.post
import io.ktor.routing.put
import io.ktor.routing.route

@KtorExperimentalLocationsAPI
fun Route.GroupsApi() {
    val gson = Gson()
    val empty = mutableMapOf<String, Any?>()

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
                    val exampleContentType = "*/*"
                    val exampleContentString = """{
                  "name" : "name",
                  "id" : "id"
                }"""

                    when (exampleContentType) {
                        "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                        "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                        else -> call.respondText(exampleContentString)
                    }
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
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "name" : "name",
              "id" : "id"
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }


        get<Paths.getGroupMembers> { _: Paths.getGroupMembers ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "organization" : "organization",
              "id" : "id",
              "username" : "username",
              "organizationRoles" : { }
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }


        get<Paths.getGroups> { _: Paths.getGroups ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "name" : "name",
              "id" : "id"
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }


        get<Paths.getSubgroups> { _: Paths.getSubgroups ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "name" : "name",
              "id" : "id"
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
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
