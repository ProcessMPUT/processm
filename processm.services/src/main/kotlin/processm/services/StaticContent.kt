package processm.services

import io.ktor.application.Application
import io.ktor.http.content.*
import io.ktor.routing.routing
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.loggedScope
import processm.core.logging.logger
import java.io.File

fun Application.staticContentModule() {
    loggedScope { logger ->
        val codeSource = File(WebServicesHost::class.java.protectionDomain.codeSource.location.toURI())
        val jar = codeSource.extension.equals("jar", true)

        routing {
            static("") {
                if (jar) {
                    logger.info("Serving static content from JAR")
                    resources("frontend-dist")
                    defaultResource("frontend-dist/index.html")
                } else {
                    logger.info("Serving static content from file system")
                    staticRootFolder =
                        codeSource.parentFile.parentFile.parentFile.resolve("processm.webui/target/classes/frontend-dist")
                    files(".")
                    default("index.html")
                }
            }
            static("api-docs") {
                if (jar) {
                    logger.info("Serving static API doc")
                    resources("openapi")
                    defaultResource("openapi/index.html")
                } else {
                    logger.info("Serving Swagger UI")
                    staticRootFolder =
                        codeSource.parentFile.parentFile.parentFile.resolve("processm.services/target/swagger-ui")
                    file("api-spec.yaml", codeSource.resolve("api-spec.yaml"))
                    files(".")
                    default("index.html")
                }
            }
        }
    }
}
