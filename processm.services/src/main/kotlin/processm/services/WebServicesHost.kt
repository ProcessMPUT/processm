package processm.services

import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import processm.core.esb.Service
import processm.core.esb.ServiceStatus
import processm.logging.loggedScope
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class WebServicesHost : Service {

    companion object {
        private const val keyStoreProperty = "ktor.security.ssl.keyStore"
    }

    private val defaultResponseTimeoutSeconds = 10
    private lateinit var engine: ApplicationEngine
    private lateinit var env: ApplicationEngineEnvironment
    override val name = "WebServicesHost"
    override var status = ServiceStatus.Unknown
        private set

    override fun register() = loggedScope {
        status = ServiceStatus.Stopped
    }

    override fun start() = loggedScope { logger ->
        logger.debug("Starting HTTP server")
        // A work-around, because it seems ktor reads properties once into its own static cache
        val args = System.getProperty("ktor.deployment.port")?.let { port ->
            arrayOf("-port=$port")
        } ?: emptyArray<String>()
        try {
            env = commandLineEnvironment(args)
        } catch (e: IllegalArgumentException) {
            if (!e.message!!.contains("-sslKeyStore")) throw e
            logger.warn("SSL certificate is not given, generating a self-signed certificate. Configure the certificate in the application.conf configuration file.")
            val certFile = File.createTempFile("ProcessM_SSL", ".jks").apply {
                parentFile.mkdirs()
                deleteOnExit()
            }
            val keyPassword = (1..100).map {
                Random.nextInt(' '.code, '~'.code).toChar()
            }.joinToString("")
                .trim() // commandLineEnvironment() trims the password internally, so we must not use blank characters at the ends

            logger.debug("Generating certificate and writing into file ${certFile.canonicalPath}")
            generateCertificate(certFile, keyAlias = "ssl", keyPassword = keyPassword)

            env = commandLineEnvironment(
                args + arrayOf(
                    "-P:ktor.security.ssl.keyStore=${certFile.canonicalPath}",
                    "-P:ktor.security.ssl.privateKeyPassword=${keyPassword}",
                    "-P:ktor.security.ssl.keyStorePassword=${keyPassword}"
                )
            )
            assert(env.config.propertyOrNull(keyStoreProperty) != null)
        }
        engine = embeddedServer(Jetty, env, configure = {
            // Nothing for now
        })
        engine.start()
        status = ServiceStatus.Started

        logger.info(
            "HTTP server started on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}"
        )
    }

    override fun stop() = loggedScope { logger ->
        logger.info(
            "Stopping HTTP server on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}"
        )
        engine.stop(3, 30, TimeUnit.SECONDS)
        env.stop()
        status = ServiceStatus.Stopped

        logger.info(
            "HTTP server stopped on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}"
        )
    }
}
