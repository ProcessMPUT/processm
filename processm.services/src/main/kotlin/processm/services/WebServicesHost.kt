package processm.services

import io.ktor.network.tls.certificates.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import org.eclipse.jetty.http.HttpCompliance
import org.eclipse.jetty.server.Server
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
        val args = listOfNotNull(System.getProperty("ktor.deployment.port")?.let { port -> "-port=$port" },
            System.getProperty("ktor.deployment.sslPort")?.let { port -> "-sslPort=$port" }
        ).toTypedArray()
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
        /*
        This is abhorrent. However, due to the following I see no other way.
        1. JettyApplicationEngineBase (the parent for JettyApplicationEngine, used by the object Jetty below)
        first calls to configureServer and only then to Server.initializeServer (from io.ktor.server.jetty)
        2. Server.addBeanToAllConnectors only adds the bean to connectors already registered in the server, not to
        connectors that will be registered in the future
        3. It is the responsibility of initializeServer to add connectors
        4. server is a protected variable in JettyApplicationEngine(Base)
        5. JettyApplicationEngine is not open (aka sealed)
        6. Copy-pasting JettyApplicationEngine would be even worse
         */
        var leakedServer: Server? = null
        engine = embeddedServer(Jetty, env, configure = {
            configureServer = {
                leakedServer = this
            }
        })
        engine.start()
        status = ServiceStatus.Started
        /*
        Change HttpCompliance to a variant that permits ambiguous URLs, which ProcessM relies on, since it transmits
        URNs in the URLs. RFC7230 seems to be the most stringent of the options available by default that supports
        this use case.
         */
        leakedServer?.addBeanToAllConnectors(HttpCompliance.RFC7230)

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
