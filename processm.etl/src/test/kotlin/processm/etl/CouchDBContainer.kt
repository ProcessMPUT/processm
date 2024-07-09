package processm.etl

import org.testcontainers.containers.GenericContainer

class CouchDBContainer<T : CouchDBContainer<T>>(dockerImageName: String = "couchdb:3") :
    GenericContainer<T>(dockerImageName) {
    companion object {
        const val PORT = 5984
    }

    init {
        withExposedPorts(PORT)
        // From https://java.testcontainers.org/features/startup_and_waits/
        // "Ordinarily Testcontainers will wait for up to 60 seconds for the container's first mapped network port to start listening."
        // Hopefully, this is sufficient for us
    }

    fun withUsername(username: String): T {
        this.username = username
        return withEnv("COUCHDB_USER", username)
    }

    fun withPassword(password: String): T {
        this.password = password
        return withEnv("COUCHDB_PASSWORD", password)
    }

    lateinit var username: String
        private set

    lateinit var password: String
        private set

    val port: Int
        get() = getMappedPort(PORT)

    val url: String
        get() = "http://$username:$password@${host}:$port/"
}