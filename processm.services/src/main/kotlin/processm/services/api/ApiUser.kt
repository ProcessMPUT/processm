package processm.services.api

import com.auth0.jwt.interfaces.Claim
import io.ktor.server.auth.*
import processm.services.api.models.OrganizationRole
import processm.services.helpers.ExceptionReason
import java.util.*

data class ApiUser(private val claims: Map<String, Claim>) : Principal {
    val userId: UUID =
        UUID.fromString(
            claims["userId"]?.asString() ?: throw ApiException(ExceptionReason.NoFieldInToken, arrayOf("userId"))
        )
    val username: String =
        claims["username"]?.asString() ?: throw ApiException(ExceptionReason.NoFieldInToken, arrayOf("username"))
    val organizations: Map<UUID, OrganizationRole> =
        claims["organizations"]?.asString()?.split(JwtAuthentication.MULTIVALUE_CLAIM_SEPARATOR)?.mapNotNull {
            if (it.isEmpty())
                return@mapNotNull null
            val (organizationId, organizationRole) = it.split(':')
            return@mapNotNull UUID.fromString(organizationId) to OrganizationRole.valueOf(organizationRole)
        }?.toMap()
            ?: throw ApiException(ExceptionReason.NoFieldInToken, arrayOf("organizations"))
}

/**
 * Authorizes [this] user in the extent that it belongs to the [organizationId] and have at least [organizationRole].
 *
 * @throws ApiException if the user is not authorized.
 */
internal fun ApiUser.ensureUserBelongsToOrganization(
    organizationId: UUID,
    organizationRole: OrganizationRole = OrganizationRole.reader
) {
    if (!organizations.containsKey(organizationId)) {
        throw ApiException(ExceptionReason.NotAMemberOfOrganization)
    } else if ((organizations[organizationId]?.ordinal ?: -1) > organizationRole.ordinal) {
        throw ApiException(ExceptionReason.InsufficientPermissionInOrganization)
    }
}
