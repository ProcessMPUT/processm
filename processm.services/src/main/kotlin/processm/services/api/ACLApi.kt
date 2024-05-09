package processm.services.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.koin.ktor.ext.inject
import processm.core.models.metadata.URN
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.Group
import processm.dbmodels.models.RoleType
import processm.helpers.mapToArray
import processm.services.api.models.OrganizationRole
import processm.services.helpers.ExceptionReason
import processm.services.logic.ACLService
import processm.services.logic.ValidationException
import processm.services.logic.toApi
import processm.services.logic.toRoleType
import java.util.*

typealias APIAccessControlEntry = processm.services.api.models.AccessControlEntry

fun Route.ACLApi() {
    val leastRoleToReadACL = RoleType.Owner
    val leastRoleToModifyACL = RoleType.Owner

    val aclService by inject<ACLService>()

    fun ApiUser.ensureCanRead(urn: URN) {
        val canRead = aclService.hasPermission(
            userId,
            urn,
            leastRoleToReadACL
        )
        if (!canRead)
            throw ApiException(ExceptionReason.ACLCannotBeRead, arrayOf(urn))
    }

    fun ApiUser.ensureCanModify(urn: URN) {
        val canModify = aclService.hasPermission(
            userId,
            urn,
            leastRoleToModifyACL
        )
        if (!canModify)
            throw ApiException(ExceptionReason.ACLCannotBeModified, arrayOf(urn))
    }

    authenticate {
        get<Paths.ACL> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanRead(urn)
            val entries = transactionMain {
                aclService.getEntries(urn).mapToArray { ace ->
                    APIAccessControlEntry(
                        ace.group.id.value,
                        ace.role.toApi(),
                        ace.group.name,
                        ace.group.organizationId?.toApi()
                    )
                }
            }
            call.respond(HttpStatusCode.OK, entries);
        }

        post<Paths.ACL> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanModify(urn)
            val entry = kotlin.runCatching { call.receiveNullable<APIAccessControlEntry>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UnparsableData)
            try {
                aclService.addEntry(urn, entry.groupId, entry.role.toRoleType())
                call.respond(HttpStatusCode.NoContent)
            } catch (_: ExposedSQLException) {
                call.respond(HttpStatusCode.Conflict)
            }
        }

        fun isLastAbleToModify(urn: URN, groupId: UUID) = !aclService.getEntries(urn)
            .any { ace -> ace.group.id.value != groupId && ace.role.name <= leastRoleToModifyACL }

        put<Paths.ACE> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanModify(urn)
            val groupId = it.groupId
            val role = kotlin.runCatching { call.receiveNullable<OrganizationRole>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UnparsableData)
            transactionMain {
                if (role.toRoleType() > leastRoleToModifyACL && isLastAbleToModify(urn, groupId))
                    throw ApiException(ExceptionReason.LastACECannotBeDowngraded)
                try {
                    aclService.updateEntry(urn, groupId, role.toRoleType())
                } catch (_: ValidationException) {
                    throw ApiException(ExceptionReason.ACENotFound)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        delete<Paths.ACE> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanModify(urn)
            val groupId = it.groupId
            transactionMain {
                if (isLastAbleToModify(urn, groupId))
                    throw ApiException(ExceptionReason.LastACECannotBeRemoved)
                try {
                    aclService.removeEntry(urn, groupId)
                } catch (_: ValidationException) {
                    throw ApiException(ExceptionReason.ACENotFound)
                }
            }
            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.AvailableGroups> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanRead(urn)
            val groups = transactionMain {
                aclService.getAvailableGroups(urn, principal.userId).mapToArray(Group::toApi)
            }
            call.respond(HttpStatusCode.OK, groups)
        }

        get<Paths.CanModifyACL> {
            val principal = call.authentication.principal<ApiUser>()!!
            val urn = URN(it.urn)
            principal.ensureCanModify(urn)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
