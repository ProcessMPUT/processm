package processm.experimental.etl

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.time.temporal.ChronoUnit

class SapHanaSQLContainer<SELF : SapHanaSQLContainer<SELF>>(dockerImageName: DockerImageName = DockerImageName.parse("saplabs/hanaexpress")) :
    JdbcDatabaseContainer<SELF>(dockerImageName) {

    val containerSystemDbPort = 39017
    val containerTenantDbPort = 39041

    init {
        addExposedPorts(containerSystemDbPort, containerTenantDbPort)
        val path = "/password.json"
        val content = JsonObject(mapOf("master_password" to JsonPrimitive(password))).toString()
        withCopyToContainer(Transferable.of(content), path)
        withCommand("--passwords-url", "file://$path", "--agree-to-sap-license")
        waitStrategy = LogMessageWaitStrategy()
            .withRegEx(".*Startup finished!*\\s")
            .withStartupTimeout(Duration.of(300, ChronoUnit.SECONDS));
    }

    override fun waitUntilContainerStarted() {
        waitStrategy.waitUntilReady(this)
    }

    val port: Int
        get() = getMappedPort(containerTenantDbPort)

    override fun getDriverClassName(): String = "com.sap.db.jdbc.Driver"

    override fun getJdbcUrl(): String = "jdbc:sap://$host:$port/?user=$username&password=$password"

    override fun getUsername(): String = "SYSTEM"

    override fun getPassword(): String = "Dycs|shral0"

    override fun getTestQueryString(): String = "SELECT 1 FROM public.tables LIMIT 1"
}