package processm

import de.odysseus.staxon.json.JsonXMLConfig
import de.odysseus.staxon.json.JsonXMLConfigBuilder
import de.odysseus.staxon.json.JsonXMLInputFactory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.locations.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.loadConfiguration
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.LogInputStream
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import processm.enhancement.kpi.AlignerKPIService
import processm.etl.PostgreSQLEnvironment
import processm.etl.metamodel.MetaModelDebeziumWatchingService.Companion.DEBEZIUM_PERSISTENCE_DIRECTORY_PROPERTY
import processm.helpers.AtomicIntegerSequence
import processm.services.JsonSerializer
import processm.services.api.Paths
import processm.services.api.models.*
import processm.services.helpers.SSE
import processm.services.helpers.readSSE
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ForkJoinPool
import java.util.zip.ZipInputStream
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.full.findAnnotation
import kotlin.test.assertEquals

@KtorExperimentalLocationsAPI
class ProcessMTestingEnvironment : CoroutineScope {

    var jdbcUrl: String? = null
        private set
    var token: String? = null
        private set
    var currentOrganizationId: UUID? = null
    var currentDataStore: DataStore? = null
    var currentDataConnector: DataConnector? = null
    var currentEtlProcess: AbstractEtlProcess? = null
    var currentWorkspaceId: UUID? = null
    var currentComponentId: UUID? = null
    var properties: HashMap<String, String> = HashMap()

    val client = HttpClient(CIO) {
        expectSuccess = true
        install(ContentNegotiation) {
            json(JsonSerializer)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 300_000
        }
    }

    override lateinit var coroutineContext: CoroutineContext
        private set
    private val sseJobs = ArrayList<Job>()

    // region Environment

    companion object {
        private fun randomMainDbName() = "processm-${UUID.randomUUID()}"

        private val sharedDbContainer: PostgreSQLContainer<*> by lazy {
            val image =
                DockerImageName.parse("timescale/timescaledb:latest-pg16-oss").asCompatibleSubstituteFor("postgres")
            //TODO investigate - it seems that if user != "postgres" processm.core.persistence.Migrator.ensureDatabaseExists fails while creating a new datastore
            val user = "postgres"
            val password = "postgres"
            val container = PostgreSQLContainer(image)
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

        private val portSequence = AtomicIntegerSequence(9000, 9999, allowOverflow = true)
    }

    private val sakilaEnv = lazy { PostgreSQLEnvironment.getSakila() }
    private var temporaryDebeziumDirectory: File? = null
    private var httpPort: Int = -1

    val sakilaJdbcUrl: String
        get() = with(sakilaEnv.value) {
            "$jdbcUrl&user=$user&password=$password"
        }

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

    fun withProperty(key: String, value: String): ProcessMTestingEnvironment {
        properties[key] = value
        return this
    }

    fun withTemporaryDebeziumStorage(): ProcessMTestingEnvironment {
        val dir = Files.createTempDirectory("processm_debezium").toFile()
        withProperty(DEBEZIUM_PERSISTENCE_DIRECTORY_PROPERTY, dir.absolutePath)
        temporaryDebeziumDirectory = dir
        return this
    }

    fun withAlignerDelay(delayInMs: Long) =
        withProperty(AlignerKPIService.QUEUE_DELAY_MS_PROPERTY, delayInMs.toString())

    @OptIn(ExperimentalCoroutinesApi::class)
    fun <T> run(block: ProcessMTestingEnvironment.() -> T) {
        try {
            loadConfiguration(true)
            properties.forEach { (k, v) -> System.setProperty(k, v) }
            jdbcUrl?.let { jdbcUrl ->
                System.setProperty(DatabaseChecker.databaseConnectionURLProperty, jdbcUrl)
                Migrator.reloadConfiguration()
            }
            httpPort = portSequence.next()
            System.setProperty("ktor.deployment.port", httpPort.toString())
            pool = Executors.newFixedThreadPool(7).asCoroutineDispatcher()
            EnterpriseServiceBus().use { esb ->
                esb.autoRegister()
                esb.startAll()
                runBlocking {
                    this@ProcessMTestingEnvironment.coroutineContext = this.coroutineContext
                    block()
                    sseJobs.forEach { it.cancelAndJoin() }
                }
            }
        } finally {
            pool.close()
            ForkJoinPool.commonPool().shutdownNow()
            client.close()
            if (sakilaEnv.isInitialized())
                sakilaEnv.value.close()
            temporaryDebeziumDirectory?.deleteRecursively()
        }
    }

    // endregion

    // region HTTP

    fun apiUrl(endpoint: String): String = "http://localhost:$httpPort/api/${endpoint}"

    inline fun <reified Endpoint> format(vararg args: Pair<String, String?>): String = format<Endpoint>(args.toMap())

    // must be public as long as get/post with Location are public, because they're inline, because reified
    fun <T> String.ktorLocationSpecificReplace(key: String, value: T?): String {
        return if (value !== null) {
            replace("{$key}", value.toString())
        } else
            this
    }

    inline fun <reified Endpoint> format(args: Map<String, String?>): String {
        var result =
            requireNotNull(Endpoint::class.findAnnotation<Location>()?.path) //StringBuilder doesn't handle replacing substrings too well
        for ((name, value) in args)
            if (value !== null)
                result = result.replace("{$name}", value)
        result = result.ktorLocationSpecificReplace("organizationId", currentOrganizationId)
        result = result.ktorLocationSpecificReplace("dataStoreId", currentDataStore?.id)
        result = result.ktorLocationSpecificReplace("dataConnectorId", currentDataConnector?.id)
        result = result.ktorLocationSpecificReplace("etlProcessId", currentEtlProcess?.id)
        result = result.ktorLocationSpecificReplace("workspaceId", currentWorkspaceId)
        result = result.ktorLocationSpecificReplace("componentId", currentComponentId)
        return result
    }

    inline fun <reified Endpoint, R> get(
        etlProcessId: AbstractEtlProcess? = null,
        noinline block: suspend HttpResponse.() -> R
    ): R =
        get(format<Endpoint>("etlProcessId" to etlProcessId?.id?.toString()), null, block)

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
            val response = client.get(apiUrl(endpoint)) {
                token?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
                if (prepare !== null)
                    prepare()
            }
            return@runBlocking response.block()
        }

    fun <R> delete(
        endpoint: String,
        block: suspend HttpResponse.() -> R
    ): R =
        runBlocking {
            val response = client.delete(apiUrl(endpoint)) {
                token?.let { token -> header(HttpHeaders.Authorization, "Bearer $token") }
            }
            return@runBlocking response.block()
        }


    inline fun <reified Endpoint, reified T, R> post(data: T?, noinline block: suspend HttpResponse.() -> R): R =
        post(format<Endpoint>(), data, block)

    inline fun <reified Endpoint, R> post(noinline block: suspend HttpResponse.() -> R): R =
        post<Any?, R>(format<Endpoint>(), null, block)

    inline fun <reified T, R> post(endpoint: String, data: T?, crossinline block: suspend HttpResponse.() -> R): R =
        runBlocking {
            val response = client.post(apiUrl(endpoint)) {
                token?.let { bearerAuth(it) }
                if (data !== null) {
                    contentType(ContentType.Application.Json)
                    setBody(data)
                }
            }
            return@runBlocking response.block()
        }

    inline fun <reified Endpoint, reified T, R> put(data: T?, noinline block: suspend HttpResponse.() -> R): R =
        put<Any?, R>(format<Endpoint>(), data, block)

    inline fun <reified T, R> put(endpoint: String, data: T?, crossinline block: suspend HttpResponse.() -> R): R =
        runBlocking {
            val response = client.put(apiUrl(endpoint)) {
                token?.let { bearerAuth(it) }
                if (data !== null) {
                    contentType(ContentType.Application.Json)
                    setBody(data)
                }
            }
            return@runBlocking response.block()
        }

    lateinit var pool: CloseableCoroutineDispatcher
    val Dispatchers.Request: CloseableCoroutineDispatcher
        get() = pool

    fun sse(endpoint: String, handler: suspend (event: SSE) -> Unit) {
        sseJobs.add(launch(Dispatchers.Request) {
            client.prepareGet(apiUrl(endpoint)) {
                token?.let { bearerAuth(it) }
                header(HttpHeaders.Accept, ContentType.Text.EventStream.toString())
            }.execute { response ->
                val channel = response.bodyAsChannel()
                while (!channel.isClosedForRead) {
                    handler(channel.readSSE())
                }
            }
        })
    }

    fun waitUntil(predicate: () -> Boolean) {
        repeat(10) {
            if (predicate()) return
            Thread.sleep(500)
        }
        throw IllegalStateException()
    }

    fun <T> waitUntilStable(get: () -> T): T {
        var previous = get()
        repeat(10) {
            Thread.sleep(500)
            val current = get()
            if (previous == current) return current
            previous = current
        }
        throw IllegalStateException("The value never stabilized, the last observed value was $previous")
    }

    fun <T> waitUntilEquals(expected: T, get: () -> T) {
        val history = ArrayList<T>()
        repeat(10) {
            val v = get()
            if (v == expected) return
            Thread.sleep(500)
            history.add(v)
        }
        throw IllegalStateException("The expected value $expected was never achieved. The history of values: $history")
    }

    // endregion

    // region UsersApi

    val organizations: List<Organization>
        get() = get<Paths.UserOrganizations, List<Organization>> {
            return@get body<List<UserRoleInOrganization>>().map { it.organization }
        }

    fun registerUser(userEmail: String, organizationName: String) =
        post(
            "/users",
            AccountRegistrationInfo(userEmail, "P@ssw0rd!", true, organizationName)
        ) {}

    fun login(login: String, password: String) =
        post("/users/session", UserCredentials(login, password)) {
            token = body<AuthenticationResult>().authorizationToken
        }

    // endregion

    // region DataStoresApi

    fun createDataStore(name: String) =
        post<Paths.DataStores, DataStore, DataStore>(DataStore(name)) {
            val ds = body<DataStore>()
            assertEquals(name, ds.name)
            return@post ds
        }

    fun createDataConnector(name: String, properties: Map<String, String>) =
        post<Paths.DataConnectors, DataConnector, DataConnector>(
            DataConnector(name = name, properties = properties)
        ) {
            val dc = body<DataConnector>()
            assertEquals(name, dc.name)
            return@post dc
        }

    /**
     * Performs the PQL query. The resulting event log is hierarchical and buffered, so it can be read many times.
     */
    @OptIn(InMemoryXESProcessing::class)
    fun pqlQuery(query: String) = get<Paths.Logs, LogInputStream>({
        parameter("query", query)
    }) {
        val config: JsonXMLConfig =
            JsonXMLConfigBuilder()
                .autoArray(true)
                .autoPrimitive(true)
                .build()
        val factory = JsonXMLInputFactory(config)

        val channel = bodyAsChannel()
        val reader = factory.createXMLStreamReader(channel.toInputStream())
        val logStream = HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(reader))

        return@get logStream
    }

    fun pqlQueryXES(query: String) = get<Paths.Logs, XMLXESInputStream>({
        parameter("query", query)
        header("Accept", "application/zip")
    }) {
        return@get ZipInputStream(bodyAsChannel().toInputStream()).use { zipStream ->
            checkNotNull(zipStream.nextEntry)
            return@use XMLXESInputStream(ByteArrayInputStream(zipStream.readAllBytes()))
        }
    }

    fun deleteLog(logIdentityId: UUID) =
        delete(format<Paths.Log>("identityId" to logIdentityId.toString())) {}

    fun deleteCurrentEtlProcess() = delete(format<Paths.EtlProcess>()) {}

    // endregion

}
