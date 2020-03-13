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
fun Route.OrganizationsApi() {
    val gson = Gson()
    val empty = mutableMapOf<String, Any?>()

    authenticate {
        route("/organizations/{organizationId}/members") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        route("/organizations") {
            post {
                val principal = call.authentication.principal<ApiUser>()

                if (principal == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                } else {
                    call.respond(HttpStatusCode.NotImplemented)
                }
            }
        }


        get<Paths.getOrganization> { _: Paths.getOrganization ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "name" : "name",
              "id" : "id",
              "isPrivate" : true
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }


        get<Paths.getOrganizationMembers> { _: Paths.getOrganizationMembers ->
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


        get<Paths.getOrganizations> { _: Paths.getOrganizations ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                val exampleContentType = "*/*"
                val exampleContentString = """{
              "name" : "name",
              "id" : "id",
              "isPrivate" : true
            }"""

                when (exampleContentType) {
                    "application/json" -> call.respond(gson.fromJson(exampleContentString, empty::class.java))
                    "application/xml" -> call.respondText(exampleContentString, ContentType.Text.Xml)
                    else -> call.respondText(exampleContentString)
                }
            }
        }


        delete<Paths.removeOrganization> { _: Paths.removeOrganization ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        delete<Paths.removeOrganizationMember> { _: Paths.removeOrganizationMember ->
            val principal = call.authentication.principal<ApiUser>()

            if (principal == null) {
                call.respond(HttpStatusCode.Unauthorized)
            } else {
                call.respond(HttpStatusCode.NotImplemented)
            }
        }


        route("/organizations/{organizationId}") {
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
