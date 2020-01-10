package processm.services

import io.ktor.application.Application
import io.ktor.http.content.*
import io.ktor.routing.routing
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.io.File

fun Application.main() {
    logger().enter()

    val codeSource = File(WebServicesHost::class.java.protectionDomain.codeSource.location.toURI())
    val jar = codeSource.extension.equals("jar", true)

    routing {
        static("") {
            if (jar) {
                logger().debug("Serving static content from JAR")
                staticBasePackage = "processm.webui"
                resources(".")
                defaultResource("index.html")
            } else {
                logger().debug("Serving static content from file system")
                staticRootFolder =
                    codeSource.parentFile.parentFile.parentFile.resolve("processm.webui/src/main/resources")
                files(".")
                default("index.html")
            }
        }
    }

    logger().exit()
}