package processm.etl

import org.testcontainers.containers.GenericContainer

/**
 * A replacement for the official testcontainers MongoDBContainer, which didn't seem to work with Mongo 7
 */
class MongoDBContainer<T : MongoDBContainer<T>>(dockerImageName: String = "mongodb:7.0") :
    GenericContainer<T>(dockerImageName) {
    companion object {
        const val PORT = 27017
    }

    init {
        withExposedPorts(PORT)
        // From https://java.testcontainers.org/features/startup_and_waits/
        // "Ordinarily Testcontainers will wait for up to 60 seconds for the container's first mapped network port to start listening."
        // Hopefully, this is sufficient for us
    }

    fun withUsername(username: String): T {
        this.username = username
        return withEnv("MONGO_INITDB_ROOT_USERNAME", username)
    }

    fun withPassword(password: String): T {
        this.password = password
        return withEnv("MONGO_INITDB_ROOT_PASSWORD", password)
    }

    lateinit var username: String
        private set

    lateinit var password: String
        private set

    val port: Int
        get() = getMappedPort(PORT)

    val url: String
        get() = "mongodb://$username:$password@$host:$port"
}