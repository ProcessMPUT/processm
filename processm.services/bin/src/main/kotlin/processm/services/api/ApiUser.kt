package processm.services.api

import com.auth0.jwt.interfaces.Claim
import io.ktor.http.*
import io.ktor.server.auth.*
import processm.services.api.models.OrganizationRole
import java.util.*

data class ApiUser(private val claims: Map<String, Claim>) : Principal {

    val userId: UUID =
        UUID.fromString(claims["userId"]?.asString() ?: throw ApiException("Token should contain 'userId' field"))
    val username: String = claims["username"]?.asString() ?: throw ApiException("Token should contain 'username' field")
    val organizations: Map<UUID, OrganizationRole> = claims["organizations"]?.asString()?.split(',')?.mapNotNull {
        if (it.isEmpty())
            return@mapNotNull null
        val (organizationId, organizationRole) = it.split(':')
        return@mapNotNull UUID.fromString(organizationId) to OrganizationRole.valueOf(organizationRole)
    }?.toMap()
        ?: throw ApiException("Token should contain 'organizations' field")
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
        throw ApiException("The user is not a member of the related organization", HttpStatusCode.Forbidden)
    } else if ((organizations[organizationId]?.ordinal ?: -1) > organizationRole.ordinal) {
        throw ApiException(
            "The user has insufficient permissions to access the related organization",
            HttpStatusCode.Forbidden
        )
    }
}
