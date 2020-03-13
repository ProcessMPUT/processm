package processm.services.api

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

object JwtAuthentication {

    fun verifier(issuer: String, secret: String): JWTVerifier = JWT
        .require(Algorithm.HMAC512(secret))
        .withIssuer(issuer)
        .build()

    fun createToken(username: String, expiration: Date, issuer: String, secret: String): String = JWT.create()
        .withSubject("Authentication")
        .withIssuer(issuer)
        .withClaim("id", username)
        .withExpiresAt(expiration)
        .sign(Algorithm.HMAC512(secret))

    fun generateSecretKey(seed: Long = 0): String = ('A'..'z').shuffled(Random(seed)).subList(0, 20).joinToString("")
}