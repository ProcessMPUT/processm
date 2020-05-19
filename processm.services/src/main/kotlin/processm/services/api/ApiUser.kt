package processm.services.api

import com.auth0.jwt.interfaces.Claim
import io.ktor.auth.Principal
import processm.services.api.models.OrganizationRole
import java.util.*

data class ApiUser(private val claims: Map<String, Claim>) : Principal {

    val userId: UUID =
        UUID.fromString(claims["userId"]?.asString() ?: throw ApiException("Token should contain 'userId' field"))
    val username: String = claims["username"]?.asString()
        ?: throw ApiException("Token should contain 'username' field")
    val organizations: Map<UUID, OrganizationRole> = claims["organizations"]?.asString()?.split(',')?.map {
            val (organizationId, organizationRole) = it.split(':')
            return@map UUID.fromString(organizationId) to OrganizationRole.valueOf(organizationRole)
        }?.toMap()
        ?: throw ApiException("Token should contain 'organizations' field")
}
