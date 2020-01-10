package processm.services

import io.ktor.application.Application
import io.ktor.network.tls.certificates.generateCertificate
import io.ktor.server.engine.ApplicationEngineEnvironment
import io.ktor.server.engine.commandLineEnvironment
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.util.KtorExperimentalAPI
import processm.core.esb.Service
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.io.File
import java.util.concurrent.TimeUnit

class WebServicesHost : Service {
    private val keyStoreProperty = "ktor.security.ssl.keyStore"
    private val charPool: List<Char> = ('a'..'z') + ('A'..'Z') + ('0'..'9')
    private var engine: NettyApplicationEngine

    @UseExperimental(KtorExperimentalAPI::class)
    constructor(args: Array<String>) {
        logger().enter()

        var env: ApplicationEngineEnvironment
        try {
            env = commandLineEnvironment(args)
        } catch (e: IllegalArgumentException) {
            if (!e.message!!.contains("-sslKeyStore"))
                throw e
            logger().warn(
                "SSL certificate is not given, generating a self-signed certificate. Use -sslKeyStore= command line option to set certificate file."
            )

            val certFile = File.createTempFile("ProcessM_SSL", ".jks").apply {
                parentFile.mkdirs()
                deleteOnExit()
            }
            val keyPassword = (1..100)
                .map { _ -> kotlin.random.Random.nextInt(Char.MIN_VALUE.toInt(), Char.MAX_VALUE.toInt()) }
                .map { i -> i.toChar() }
                .joinToString("")

            logger().debug("Generating certificate and writing into file ${certFile.canonicalPath}")
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

        logger().debug("Setting up HTTP server")
        engine = embeddedServer(Netty, env)

        logger().exit()
    }

    override fun start() {
        logger().enter()

        logger().debug("Starting HTTP server on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}")
        engine.start()

        logger().info("HTTP server started on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}")
        logger().exit()
    }

    override fun stop() {
        logger().enter()

        logger().info("Stopping HTTP server on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}")
        engine.stop(15, 30, TimeUnit.SECONDS)

        logger().info("HTTP server stopped on port ${engine.environment.config.property("ktor.deployment.sslPort").getString()}")
        logger().exit()
    }
}