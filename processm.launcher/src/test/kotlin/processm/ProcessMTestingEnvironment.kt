package processm

import com.google.gson.Gson
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.locations.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.dbcp2.DriverManagerConnectionFactory
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.loadConfiguration
import processm.core.persistence.Migrator
import processm.services.api.Paths
import processm.services.api.models.*
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertEquals

@OptIn(KtorExperimentalLocationsAPI::class)
class ProcessMTestingEnvironment {

    var jdbcUrl: String? = null
        private set
    private var token: String? = null
    var currentOrganizationId: UUID? = null
    var currentDataStore: DataStore? = null
    var currentDataConnector: DataConnector? = null
    var currentEtlProcess: AbstractEtlProcess? = null

    // region Environment

    companion object {
        private fun randomMainDbName() = "processm-${UUID.randomUUID()}"

        private val sharedDbContainer: PostgreSQLContainer<*> by lazy {
            val image =
                DockerImageName.parse("timescale/timescaledb:latest-pg12-oss").asCompatibleSubstituteFor("postgres")
            //TODO investigate - it seems that if user != "postgres" processm.core.persistence.Migrator.ensureDatabaseExists fails while creating a new datastore
            val user = "postgres"
            val password = "postgres"
            val container = PostgreSQLContainer<PostgreSQLContainer<*>>(image)
                .withDatabaseName("postgres")
                .withUsername(user)
                .withPassword(password)
                .withReuse(false)
            Startables.deepStart(listOf(container)).join()
            return@lazy container
        }


        private fun ddlQuery(query: String) =
            sharedDbContainer.createConnection("").use {
                it.prepareStatement(query).execute()
            }

        private fun createDatabase(dbName: String) = ddlQuery("create database \"$dbName\"")

        private fun jdbcUrlForDb(dbName: String): String {
            val ip = sharedDbContainer.containerIpAddress
            val port = sharedDbContainer.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)
            val user = sharedDbContainer.username
            val password = sharedDbContainer.password
            return "jdbc:postgresql://$ip:$port/$dbName?loggerLevel=OFF&user=$user&password=$password"
        }

        private val sakilaJdbcUrl: String by lazy {
            val dbName = "sakila"
            val schemaScript = "sakila/postgres-sakila-db/postgres-sakila-schema.sql"
            val insertScript = "sakila/postgres-sakila-db/postgres-sakila-insert-data.sql"
            createDatabase(dbName)
            val url = jdbcUrlForDb(dbName)

            DriverManagerConnectionFactory(url).createConnection().use { connection ->
                connection.autoCommit = false
                connection.createStatement().use { s ->
                    s.execute(this::class.java.classLoader.getResource(schemaScript)!!.readText())
                    s.execute(this::class.java.classLoader.getResource(insertScript)!!.readText())
                }
                connection.commit()
            }
            return@lazy url
        }
    }

    val sakilaJdbcUrl: String
        get() = Companion.sakilaJdbcUrl

    fun withPreexistingDatabase(jdbcUrl: String): ProcessMTestingEnvironment {
        this.jdbcUrl = jdbcUrl
        return this
    }

    fun withFreshDatabase(): ProcessMTestingEnvironment {
        val freshDbName = randomMainDbName()
        createDatabase(freshDbName)
        this.jdbcUrl = jdbcUrlForDb(freshDbName)
        return this
    }

    fun <T> run(block: ProcessMTestingEnvironment.() -> T) {
        try {
            loadConfiguration(true)
            jdbcUrl?.let { jdbcUrl ->
                System.setProperty("PROCESSM.CORE.PERSISTENCE.CONNECTION.URL", jdbcUrl)
                Migrator.reloadConfiguration()
            }
            EnterpriseServiceBus().use { esb ->
                esb.autoRegister()
                esb.startAll()
                block()
            }
        } finally {
            ForkJoinPool.commonPool().shutdownNow()
        }
    }

    // endregion

    // region HTTP

    //TODO at the very least port should not be hardcoded
    fun apiUrl(endpoint: String): String = "http://localhost:2080/api/${endpoint}"

    inline fun <reified Endpoint> format(vararg args: Pair<String, String>): String = format<Endpoint>(args.toMap())

    // must be public as long as get/post with Location are public, because they're inline, because reified
    fun <T> String.ktorLocationSpecificReplace(key: String, value: T?): String {
        return if (value !== null) {
            replace("{$key}", value.toString())
        } else
            this
    }

    inline fun <reified Endpoint> format(args: Map<String, String>): String {
        var result =
            requireNotNull(Endpoint::class.findAnnotation<Location>()?.path) //StringBuilder doesn't handle replacing substrings too well
        for ((name, value) in args)
            result = result.replace("{$name}", value)
        result = result.ktorLocationSpecificReplace("organizationId", currentOrganizationId)
        result = result.ktorLocationSpecificReplace("dataStoreId", currentDataStore?.id)
        result = result.ktorLocationSpecificReplace("dataConnectorId", currentDataConnector?.id)
        result = result.ktorLocationSpecificReplace("etlProcessId", currentEtlProcess?.id)
        return result
    }

    inline fun <reified Endpoint, R> get(noinline block: suspend HttpResponse.() -> R): R =
        get(format<Endpoint>(), null, block)

    inline fun <reified Endpoint, R> get(
        noinline prepare: (suspend HttpRequestBuilder.() -> Unit)?,
        noinline block: suspend HttpResponse.() -> R
    ): R =
        get(format<Endpoint>(), prepare, block)

    fun <R> get(endpoint: String, block: suspend HttpResponse.() -> R): R = get(endpoint, null, block)

    fun <R> get(
        endpoint: String,
        prepare: (suspend HttpRequestBuilder.() -> Unit)?,
        block: suspend HttpResponse.() -> R
    ): R =
        runBlocking {
            HttpClient(CIO).use { client ->
                val response = client.get<HttpResponse>(apiUrl(endpoint)) {
                    token?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
                    if (prepare !== null)
                        prepare()
                }
                return@runBlocking response.block()
            }
        }

    fun <R> delete(
        endpoint: String,
        block: suspend HttpResponse.() -> R
    ): R =
        runBlocking {
            HttpClient(CIO).use { client ->
                val response = client.delete<HttpResponse>(apiUrl(endpoint)) {
                    token?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
                }
                return@runBlocking response.block()
            }
        }


    inline fun <reified Endpoint, T, R> post(data: T?, noinline block: suspend HttpResponse.() -> R): R =
        post(format<Endpoint>(), data, block)

    inline fun <reified Endpoint, R> post(noinline block: suspend HttpResponse.() -> R): R =
        post(format<Endpoint>(), null, block)

    fun <T, R> post(endpoint: String, data: T?, block: suspend HttpResponse.() -> R): R =
        runBlocking {
            HttpClient(CIO).use { client ->
                val response = client.post<HttpResponse>(apiUrl(endpoint)) {
                    token?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
                    if (data !== null) {
                        contentType(ContentType.Application.Json)
                        body = Gson().toJson(data)
                    }
                }
                return@runBlocking response.block()
            }
        }

    // endregion

    // region UsersApi

    val organizations: List<UserOrganization>
        get() = get<Paths.UserOrganizations, List<UserOrganization>> {
            return@get deserialize<UserOrganizationCollectionMessageBody>().data.toList()
        }

    fun registerUser(userEmail: String, organizationName: String) =
        post(
            "/users",
            mapOf("data" to mapOf("userEmail" to userEmail, "organizationName" to organizationName))
        ) {}

    fun login(login: String, password: String) =
        post("/users/session", mapOf("data" to mapOf("login" to login, "password" to password))) {
            token = deserialize<AuthenticationResultMessageBody>().data.authorizationToken
        }

    // endregion

    // region DataStoresApi

    fun createDataStore(name: String) =
        post<Paths.DataStores, DataStoreMessageBody, DataStore>(DataStoreMessageBody(DataStore(name))) {
            val ds = deserialize<DataStoreMessageBody>().data
            assertEquals(name, ds.name)
            return@post ds
        }

    fun createDataConnector(name: String, properties: Map<String, String>) =
        post<Paths.DataConnectors, DataConnectorMessageBody, DataConnector>(
            DataConnectorMessageBody(DataConnector(name = name, properties = properties))
        ) {
            val dc = deserialize<DataConnectorMessageBody>().data
            assertEquals(name, dc.name)
            return@post dc
        }

    fun pqlQuery(query: String) = get<Paths.Logs, Array<Any>>({
        parameter("query", query)
        accept(ContentType.Application.Json)
    }) {
        return@get deserialize<QueryResultCollectionMessageBody>().data
    }

    fun deleteLog(logIdentityId: UUID) =
        delete(format<Paths.Log>("identityId" to logIdentityId.toString())) {}

    fun deleteCurrentEtlProcess() = delete(format<Paths.EtlProcess>()) {}

    // endregion

}
