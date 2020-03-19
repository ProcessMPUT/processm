package processm.services.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.TokenExpiredException
import java.lang.Exception
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
            throw TokenExpiredException("The token exceeded allowed expiration")
        }

        val username = expiredToken.claims["id"]?.asString()
            ?: throw UnsupportedOperationException("Token should contain 'id' field")
        val newExpirationDate = Instant
            .now()
            .plusMillis(expiredToken.expiresAt.time - expiredToken.issuedAt.time)

        return createToken(
            username,
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

    fun createToken(username: String, expiration: Instant, issuer: String, secret: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("id", username)
        .withExpiresAt(Date(expiration.toEpochMilli()))
        .withIssuedAt(Date())
        .withNotBefore(Date())
        .sign(Algorithm.HMAC512(secret))

    fun generateSecretKey(seed: Long = 0): String = ('A'..'z').shuffled(Random(seed)).subList(0, 20).joinToString("")
}