package processm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.locations.*
import kotlinx.coroutines.runBlocking
import org.apache.commons.dbcp2.DriverManagerConnectionFactory
import org.junit.jupiter.api.assertThrows
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.loadConfiguration
import processm.core.persistence.Migrator
import processm.services.api.Paths
import processm.services.api.defaultSampleSize
import processm.services.api.models.*
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.reflect.full.findAnnotation
import kotlin.test.*

fun HttpResponse.assertContentType(expected: ContentType) =
    assertTrue(
        contentType()?.match(expected) == true,
        "Unexpected content type: `${contentType()}'. Expected: `$expected'"
    )

/**
 * Ducktyping for raw results of JSON deserialization. Anything indexed with a string is assumed to be a map, and anything
 * indexed with an integer - to be a list.
 *
 * Intended usage:
 * ```
 * with(ducktyping) { ... }
 * ```
 */
object ducktyping {

    operator fun Any?.get(key: String): Any? {
        assertIs<Map<String, *>>(this)
        return this[key]
    }

    operator fun Any?.get(key: Int): Any? {
        assertIs<List<*>>(this)
        return this[key]
    }
}

suspend inline fun <reified T> HttpResponse.deserialize(): T {
    assertContentType(ContentType.Application.Json)
    // TODO: replace GSON with kotlinx/serialization
    val gsonBuilder = GsonBuilder()
    // Correctly serialize/deserialize LocalDateTime
    gsonBuilder.registerTypeAdapter(LocalDateTime::class.java, object : TypeAdapter<LocalDateTime>() {
        override fun write(out: JsonWriter, value: LocalDateTime?) {
            out.value(value?.toString())
        }

        override fun read(`in`: JsonReader): LocalDateTime = LocalDateTime.parse(`in`.nextString())
    })
    return gsonBuilder.create().fromJson(receive<String>(), T::class.java)
}

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
            val image = DockerImageName.parse("timescale/timescaledb:latest-pg12").asCompatibleSubstituteFor("postgres")
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

class IntegrationTests {

    @Test
    fun `complete workflow for testing an incorrect ETL process`() {
        val samplingEtlProcessName = "blah"
        val query = "An incorrect query"
        ProcessMTestingEnvironment().withFreshDatabase().run {
            registerUser("test@example.com", "some organization")
            login("test@example.com", "pass")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector = createDataConnector("dc1", mapOf("connection-string" to jdbcUrl!!))
            val initialDefinition = AbstractEtlProcess(
                samplingEtlProcessName,
                currentDataConnector?.id!!,
                EtlProcessType.jdbc,
                configuration = JdbcEtlProcessConfiguration(
                    query,
                    true,
                    false,
                    JdbcEtlColumnConfiguration("trace_id", "trace_id"),
                    JdbcEtlColumnConfiguration("event_id", "event_id"),
                    emptyArray(),
                    lastEventExternalId = "0"
                )
            )
            val samplingEtlProcess = post<Paths.SamplingEtlProcess, EtlProcessMessageBody, AbstractEtlProcess>(
                EtlProcessMessageBody(initialDefinition)
            ) {
                return@post deserialize<EtlProcessMessageBody>().data
            }
            assertEquals(samplingEtlProcessName, samplingEtlProcess.name)
            assertEquals(currentDataConnector?.id, samplingEtlProcess.dataConnectorId)
            assertNotNull(samplingEtlProcess.id)

            currentEtlProcess = samplingEtlProcess

            val info = runBlocking {
                for (i in 0..10) {
                    Thread.sleep(1000)
                    val info = get<Paths.EtlProcess, EtlProcessInfo> {
                        return@get deserialize<EtlProcessInfo>()
                    }
                    if (!info.errors.isNullOrEmpty())
                        return@runBlocking info
                }
                error("Process did not fail in the prescribed amount of time")
            }
            assertFalse { info.errors.isNullOrEmpty() }
            val logIdentityId = info.logIdentityId

            deleteLog(logIdentityId)
            with(pqlQuery("where log:identity:id=$logIdentityId")) {
                assertTrue { isEmpty() }
            }

            deleteCurrentEtlProcess()
            assertThrows<ClientRequestException> { get<Paths.EtlProcess, Unit> {} }
        }
    }

    @Test
    fun `complete workflow for testing ETL process`() {
        val samplingEtlProcessName = "blah"

        val query = """
            SELECT * FROM (
SELECT "concept:name", "lifecycle:transition", "concept:instance", "time:timestamp", "trace_id", row_number() OVER (ORDER BY "time:timestamp", "concept:instance") AS "event_id" FROM (
        SELECT 
            'rent' AS "concept:name",
            'start' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            rental_date AS "time:timestamp",
            inventory_id AS "trace_id"
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION ALL
        SELECT 
            'rent' AS "concept:name",
            'complete' AS "lifecycle:transition",
            rental_id AS "concept:instance",
            return_date AS "time:timestamp",
            inventory_id AS "trace_id"
        FROM rental
        WHERE return_date IS NOT NULL
    UNION ALL
        SELECT
            'pay' AS "concept:name",
            'complete' AS "lifecycle:transition",
            payment_id AS "concept:instance",
            payment_date AS "time:timestamp",
            inventory_id AS "trace_id"
        FROM payment p JOIN rental r ON r.rental_id=p.rental_id
        WHERE payment_date IS NOT NULL    
) sub ) core
 WHERE "event_id" > CAST(? AS bigint)
 ORDER BY "event_id"
    """.trimIndent()

        ProcessMTestingEnvironment().withFreshDatabase().run {
            registerUser("test@example.com", "some organization")
            login("test@example.com", "pass")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector = createDataConnector("dc1", mapOf("connection-string" to sakilaJdbcUrl))

            val initialDefinition = AbstractEtlProcess(
                samplingEtlProcessName,
                currentDataConnector?.id!!,
                EtlProcessType.jdbc,
                configuration = JdbcEtlProcessConfiguration(
                    query,
                    true,
                    false,
                    JdbcEtlColumnConfiguration("trace_id", "trace_id"),
                    JdbcEtlColumnConfiguration("event_id", "event_id"),
                    emptyArray(),
                    lastEventExternalId = "0"
                )
            )

            val samplingEtlProcess = post<Paths.SamplingEtlProcess, EtlProcessMessageBody, AbstractEtlProcess>(
                EtlProcessMessageBody(initialDefinition)
            ) {
                return@post deserialize<EtlProcessMessageBody>().data
            }
            assertEquals(samplingEtlProcessName, samplingEtlProcess.name)
            assertEquals(currentDataConnector?.id, samplingEtlProcess.dataConnectorId)
            assertNotNull(samplingEtlProcess.id)

            currentEtlProcess = samplingEtlProcess
            val info = runBlocking {
                for (i in 0..10) {
                    val info = get<Paths.EtlProcess, EtlProcessInfo> {
                        return@get deserialize<EtlProcessInfo>()
                    }
                    if (info.lastExecutionTime !== null)
                        return@runBlocking info
                    Thread.sleep(1000)
                }
                error("The ETL process was not executed in the prescribed amount of time")
            }
            assertTrue { info.errors.isNullOrEmpty() }
            val logIdentityId = info.logIdentityId

            val logs: Array<Any> = pqlQuery("where log:identity:id=$logIdentityId")

            assertEquals(1, logs.size)
            val log = logs[0]
            with(ducktyping) {
                assertEquals(logIdentityId.toString(), log["log"]["id"]["@value"])
                val traces = log["log"]["trace"] as List<*>
                val nItems = 1 + traces.size + traces.sumOf { trace -> (trace["event"] as List<*>).size }
                assertTrue { nItems <= defaultSampleSize }
            }

            deleteLog(logIdentityId)
            with(pqlQuery("where log:identity:id=$logIdentityId")) {
                assertTrue { isEmpty() }
            }

            deleteCurrentEtlProcess()
            assertThrows<ClientRequestException> { get<Paths.EtlProcess, Unit> {} }
        }
    }

}