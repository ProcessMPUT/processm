package processm.services.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.HttpStatusCode
import java.time.Duration
import java.time.Instant
import java.util.*

object JwtAuthentication {

    private fun createProlongingTokenVerifier(issuer: String, secret: String, acceptableExpiration: Duration): JWTVerifier = JWT
        .require(Algorithm.HMAC512(secret))
        .acceptExpiresAt(acceptableExpiration.seconds)
        .withIssuer(issuer)
        .build()

    fun verifyAndProlongToken(encodedToken: String, issuer: String, secret: String, acceptableExpiration: Duration): String {
        var expiredToken = createProlongingTokenVerifier(issuer, secret, acceptableExpiration)
            .verify(encodedToken)

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
            newExpirationDate,
            expiredToken.issuer,
            secret
        )
    }

    fun createVerifier(issuer: String, secret: String): JWTVerifier = JWT
        .require(Algorithm.HMAC512(secret))
        .withIssuer(issuer)
        .acceptLeeway(0)
        .build()

    fun createToken(userId: Long, username: String, expiration: Instant, issuer: String, secret: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("userId", userId)
        .withClaim("username", username)
        .withExpiresAt(Date(expiration.toEpochMilli()))
        .withIssuedAt(Date())
        .withNotBefore(Date())
        .sign(Algorithm.HMAC512(secret))

    fun generateSecretKey(seed: Long = 0): String = ('A'..'z').shuffled(Random(seed)).subList(0, 20).joinToString("")
}