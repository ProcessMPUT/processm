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
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.Organization
import processm.dbmodels.toEntityID
import processm.helpers.mapToArray
import processm.services.api.models.OrganizationMember
import processm.services.api.models.OrganizationRole
import processm.services.helpers.ExceptionReason
import processm.services.logic.*
import processm.services.respondCreated

@KtorExperimentalLocationsAPI
fun Route.OrganizationsApi() {
    val organizationService by inject<OrganizationService>()

    authenticate {
        // region Organizations
        get<Paths.Organizations> { _ ->
            val principal = call.authentication.principal<ApiUser>()!!

            val organizations = transactionMain {
                organizationService.getAll(principal.userId).map(Organization::toApi)
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

        put<Paths.SubOrganization> { path ->
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
                    ExceptionReason.NotADirectSuborganization, path.subOrganizationId, path.organizationId
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
                ExceptionReason.CannotChangeRole
            )
            organizationService.updateMember(params.organizationId, params.userId, member.organizationRole.toRoleType())

            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.OrganizationMember> { params ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(params.organizationId, OrganizationRole.owner)

            params.userId.validateNot(principal.userId, ExceptionReason.CannotDelete)
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

        // region Objects

        get<Paths.OrganizationSoleOwnership> { path ->
            val principal = call.authentication.principal<ApiUser>()!!

            principal.ensureUserBelongsToOrganization(path.organizationId)

            val objects = transactionMain {
                organizationService.getSoleOwnershipURNs(path.organizationId).mapToArray {
                    it.toEntityID().toApi()
                }
            }
            call.respond(objects)
        }

        // endregion
    }
}
