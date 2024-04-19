package processm.services.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.server.config.*
import processm.services.api.models.OrganizationRole
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.*

object JwtAuthentication {

    const val MULTIVALUE_CLAIM_SEPARATOR: String = ","

    private fun createProlongingTokenVerifier(
        issuer: String, secret: String, acceptableExpiration: Duration
    ): JWTVerifier =
        JWT.require(Algorithm.HMAC512(secret)).acceptExpiresAt(acceptableExpiration.seconds).withIssuer(issuer).build()

    /**
     * @param createToken Exposed to provide an opportunity to update the claims inside the token
     */
    fun verifyAndProlongToken(
        encodedToken: String, issuer: String, secret: String, acceptableExpiration: Duration,
        createToken: (UUID, String, Map<UUID, OrganizationRole>, Instant, String, String) -> String = JwtAuthentication::createToken
    ): String {
        var expiredToken = createProlongingTokenVerifier(issuer, secret, acceptableExpiration).verify(encodedToken)

        if (Duration.between(expiredToken.expiresAt.toInstant(), Instant.now()) > acceptableExpiration) {
            throw ApiException("The token exceeded allowed expiration", HttpStatusCode.Unauthorized)
        }
        val apiUser = ApiUser(expiredToken.claims)
        val newExpirationDate = Instant
            .now()
            .plusMillis(expiredToken.expiresAt.time - expiredToken.issuedAt.time)

        return createToken(
            apiUser.userId,
            apiUser.username,
            apiUser.organizations,
            newExpirationDate,
            expiredToken.issuer,
            secret
        )
    }

    fun createVerifier(issuer: String, secret: String): JWTVerifier =
        JWT.require(Algorithm.HMAC512(secret)).withIssuer(issuer).acceptLeeway(0).build()

    fun createToken(
        userId: UUID,
        username: String,
        organizations: Map<UUID, OrganizationRole>,
        expiration: Instant,
        issuer: String,
        secret: String
    ): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("userId", userId.toString())
        .withClaim("username", username)
        .withClaim(
            "organizations",
            organizations.map { "${it.key}:${it.value}" }.joinToString(separator = MULTIVALUE_CLAIM_SEPARATOR)
        )
        .withExpiresAt(Date(expiration.toEpochMilli()))
        .withIssuedAt(Date())
        .withNotBefore(Date())
        .sign(Algorithm.HMAC512(secret))

    internal fun generateSecretKey(): String = ('A'..'z').shuffled(SecureRandom()).subList(0, 20).joinToString("")

    fun getSecretKey(config: ApplicationConfig): String =
        config.propertyOrNull("secret")?.getString() ?: randomSecretKey

    private val randomSecretKey = generateSecretKey()
}
