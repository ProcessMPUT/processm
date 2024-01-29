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
import processm.core.helpers.mapToArray
import processm.core.persistence.connection.transactionMain
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.logic.*
import processm.services.respondCreated

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {
    val organizationService by inject<OrganizationService>()

    authenticate {
        // region Organizations
        get<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()
            // This method is available to authorized users only
            // Access control: only non-private organizations are available to every authorized user
            principal.validateNotNull(Reason.Unauthorized)

            val rawOrganizations = organizationService.getAll(true) +
                    principal!!.organizations.keys.map { organizationService.get(it) }
            val organizations = rawOrganizations.mapTo(HashSet()) { org ->
                assert(!org.isPrivate)
                org.toApi()
            }

            call.respond(organizations)
        }

        post<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!
            val newOrganization = call.receive<ApiOrganization>()

            val newOrg = organizationService.create(
                name = newOrganization.name,
                isPrivate = newOrganization.isPrivate,
                ownerUserId = principal.userId
            ).toApi()

            call.respond(HttpStatusCode.Created, newOrg)
        }

        get<Paths.Organization> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.reader)

            val organization = organizationService.get(path.organizationId)

            call.respond(HttpStatusCode.OK, organization.toApi())
        }

        delete<Paths.Organization> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.owner)

            organizationService.remove(path.organizationId)

            call.respond(HttpStatusCode.NoContent)
        }

        put<Paths.Organization> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)

            val newOrg = call.receive<ApiOrganization>()

            organizationService.update(path.organizationId) {
                this.name = newOrg.name
                this.isPrivate = newOrg.isPrivate
                // TODO: currently, we do not support change of other attributes
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.SubOrganizations> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.reader)
            val organizations = organizationService.getSubOrganizations(path.organizationId)
            call.respond(organizations.map { it.toApi() })
        }

        post<Paths.SubOrganization> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)
            principal.ensureUserBelongsToOrganization(path.subOrganizationId, OrganizationRole.owner)

            organizationService.attachSubOrganization(path.organizationId, path.subOrganizationId)

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.SubOrganization> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            // One of the two permissions is sufficient - hence the try-catch
            try {
                principal.ensureUserBelongsToOrganization(path.organizationId, OrganizationRole.writer)
            } catch (e: ApiException) {
                principal.ensureUserBelongsToOrganization(path.subOrganizationId, OrganizationRole.owner)
            }

            transactionMain {

                organizationService.get(path.subOrganizationId).parentOrganization?.id?.value?.validate(
                    path.organizationId,
                    message = "The organization ${path.subOrganizationId} is not a direct sub-organization of the organization ${path.organizationId}"
                )

                organizationService.detachSubOrganization(path.subOrganizationId)
            }

            call.respond(HttpStatusCode.NoContent)
        }

        // end region

        // region Organization members
        get<Paths.OrganizationMembers> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId)

            val members = transactionMain {
                organizationService.getMembers(params.organizationId).mapToArray {
                    OrganizationMember(
                        id = it.user.id.value,
                        email = it.user.email,
                        organizationRole = it.role.toApi()
                    )
                }
            }
            call.respond(HttpStatusCode.OK, members)
        }

        post<Paths.OrganizationMembers> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)
            val member = call.receive<OrganizationMember>()

            val user = organizationService.addMember(
                params.organizationId,
                requireNotNull(member.email),
                member.organizationRole.toRoleType()
            )

            call.respondCreated(Paths.OrganizationMember(params.organizationId, user.id.value))
        }

        patch<Paths.OrganizationMember> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)
            val member = call.receive<OrganizationMember>()

            params.userId.validateNot(
                principal.userId,
                Reason.UnprocessableResource,
                "Cannot change role of the current user."
            )
            organizationService.updateMember(params.organizationId, params.userId, member.organizationRole.toRoleType())

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.OrganizationMember> { params ->
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
                .mapToArray { it.toApi() }

            call.respond(HttpStatusCode.OK, organizationGroups)
        }
        // endregion
    }
}
