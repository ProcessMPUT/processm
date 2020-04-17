package processm.services.api

import com.auth0.jwt.interfaces.Claim
import io.ktor.auth.Principal

data class ApiUser(private val claims: Map<String, Claim>): Principal {

    val userId: Long = claims["userId"]?.asLong() ?: throw ApiException("Token should contain 'userId' field")
    val username: String = claims["username"]?.asString() ?: throw ApiException("Token should contain 'username' field")
}
