package processm

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.locations.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import org.testcontainers.lifecycle.Startables
import processm.core.Brand
import processm.core.helpers.inverse
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.etl.DBMSEnvironment
import processm.etl.MSSQLEnvironment
import processm.etl.MySQLEnvironment
import processm.etl.PostgreSQLEnvironment
import processm.services.api.Paths
import processm.services.api.defaultSampleSize
import processm.services.api.models.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.*

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

private fun <T, R> Iterable<T>.parallelMap(transform: suspend (T) -> R): List<R> {
    val result = ArrayList<Pair<Int, R>>()
    runBlocking {
        val channel = Channel<Pair<Int, R>>()
        var n = 0
        forEachIndexed { index, t ->
            launch {
                channel.send(index to transform(t))
            }
            ++n
        }
        for (i in 0 until n)
            result.add(channel.receive())
    }
    result.sortBy { it.first }
    return result.map { it.second }
}

@OptIn(KtorExperimentalLocationsAPI::class)
class IntegrationTests {

    @Test
    fun `complete workflow for testing an incorrect ETL process`() {
        val samplingEtlProcessName = "blah"
        val query = "An incorrect query"
        ProcessMTestingEnvironment().withFreshDatabase().run {
            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector = createDataConnector("dc1", mapOf("connection-string" to jdbcUrl!!))
            val initialDefinition = AbstractEtlProcess(
                name = samplingEtlProcessName,
                dataConnectorId = currentDataConnector?.id!!,
                type = EtlProcessType.jdbc,
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
            val samplingEtlProcess =
                post<Paths.SamplingEtlProcess, AbstractEtlProcess, AbstractEtlProcess>(initialDefinition) {
                    return@post body<AbstractEtlProcess>()
                }
            assertEquals(samplingEtlProcessName, samplingEtlProcess.name)
            assertEquals(currentDataConnector?.id, samplingEtlProcess.dataConnectorId)
            assertNotNull(samplingEtlProcess.id)

            currentEtlProcess = samplingEtlProcess

            val info = runBlocking {
                for (i in 0..10) {
                    delay(1000)
                    val info = get<Paths.EtlProcess, EtlProcessInfo> {
                        return@get body<EtlProcessInfo>()
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
            assertFailsWith<ClientRequestException> {
                get<Paths.EtlProcess, Unit> {}
            }
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
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector = createDataConnector("dc1", mapOf("connection-string" to sakilaJdbcUrl))

            val initialDefinition = AbstractEtlProcess(
                name = samplingEtlProcessName,
                dataConnectorId = currentDataConnector?.id!!,
                type = EtlProcessType.jdbc,
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

            val samplingEtlProcess =
                post<Paths.SamplingEtlProcess, AbstractEtlProcess, AbstractEtlProcess>(initialDefinition) {
                    return@post body<AbstractEtlProcess>()
                }
            assertEquals(samplingEtlProcessName, samplingEtlProcess.name)
            assertEquals(currentDataConnector?.id, samplingEtlProcess.dataConnectorId)
            assertNotNull(samplingEtlProcess.id)

            currentEtlProcess = samplingEtlProcess
            val info = runBlocking {
                for (i in 0..10) {
                    val info = get<Paths.EtlProcess, EtlProcessInfo> {
                        return@get body<EtlProcessInfo>()
                    }
                    if (info.lastExecutionTime !== null)
                        return@runBlocking info
                    delay(1000)
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
            assertFailsWith<ClientRequestException> {
                get<Paths.EtlProcess, Unit> {}
            }
        }
    }

    private class FauxSendmail {
        private val bodyFile: File
        private val sendmail: File

        init {
            Assumptions.assumeTrue(File("/bin/sh").canExecute())
            with(Files.createTempDirectory(Brand.name).toFile()) {
                deleteOnExit()
                bodyFile = File(this, "body")
                bodyFile.deleteOnExit()
                sendmail = File(this, "sendmail")
                sendmail.deleteOnExit()
                println(sendmail.absolutePath)
                sendmail.outputStream().use { stream ->
                    stream.write(
                        """#!/bin/sh
                |exec /bin/cat >${bodyFile.absolutePath}
            """.trimMargin().encodeToByteArray()
                    )
                }
                Assumptions.assumeTrue(sendmail.setExecutable(true))
            }
        }

        val executable: String
            get() = sendmail.absolutePath

        val lastEmailBody: String
            get() = bodyFile.readText()
    }

    @Test
    fun `complete workflow for resetting password`() {
        val sendmail = FauxSendmail()
        ProcessMTestingEnvironment()
            .withProperty("processm.email.sendmailExecutable", sendmail.executable)
            .withFreshDatabase().run {
                registerUser("text@example.com", "some organization")
                post<Paths.ResetPasswordRequest, ResetPasswordRequest, Unit>(ResetPasswordRequest("text@example.com")) {
                    assertEquals(HttpStatusCode.Accepted, status)
                }
                //wait for the email
                val re = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}", RegexOption.IGNORE_CASE)
                val token = runBlocking {
                    for (i in 1..15) {
                        try {
                            re.find(sendmail.lastEmailBody)?.value?.let { return@runBlocking it }
                        } catch (e: IOException) {
                            delay(100L)
                        }
                    }
                    error("Mail was not sent in time limit.")
                }

                assertNotNull(token)
                post("/reset-password/$token", PasswordChange("", "newPassword")) {
                    assertEquals(HttpStatusCode.OK, status)
                }
                login("text@example.com", "newPassword")
            }
    }

    @OptIn(InMemoryXESProcessing::class)
    private fun <T : DBMSEnvironment<*>> `complete workflow for automatic ETL process with Sakila`(
        sakila: T,
        populate: T.() -> Unit
    ) {
        ProcessMTestingEnvironment()
            .withTemporaryDebeziumStorage()
            .withFreshDatabase()
            .withProperty("processm.logs.limit.trace", "3000")
            .run {
                registerUser("test@example.com", "some organization")
                login("test@example.com", "P@ssw0rd!")
                currentOrganizationId = organizations.single().id
                currentDataStore = createDataStore("datastore")
                post<Paths.ConnectionTest, DataConnector, Unit>(DataConnector(properties = sakila.connectionProperties)) {
                    assertEquals(HttpStatusCode.NoContent, status)
                }
                currentDataConnector = createDataConnector("dc1", sakila.connectionProperties)
                val relationshipGraph = get<Paths.RelationshipGraph, CaseNotion> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<CaseNotion>()
                }
                // Originally, the test was supposed to verify the number of items to detect unexpected changes.
                // Turns out, the numbers differ from DB to DB, i.e., they are not very meaningful
                // For example, there are 21 classes in Postgres, 17 in MySQL and 168 in MSSQL (preliminary)
                // Similarily, there are 354 case notions in Postgres and 52 in MySQL
                assertTrue { relationshipGraph.classes.isNotEmpty() }
                assertTrue { relationshipGraph.edges.isNotEmpty() }
                val caseNotions = get<Paths.CaseNotionSuggestions, Array<CaseNotion>> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<Array<CaseNotion>>()
                }
                assertTrue { caseNotions.isNotEmpty() }

                //Find the following CaseNotion to ensure the rest of the test works correctly
                //CaseNotion(classes={11=city, 5=country, 4=address, 21=store}, edges=[CaseNotionEdgesInner(sourceClassId=11, targetClassId=5), CaseNotionEdgesInner(sourceClassId=4, targetClassId=11), CaseNotionEdgesInner(sourceClassId=21, targetClassId=4)])
                val caseNotion = caseNotions.single { caseNotion ->
                    if (caseNotion.classes.values.toSet() != setOf("city", "country", "address", "store"))
                        return@single false
                    val nameToId = caseNotion.classes.inverse()
                    val storeId = nameToId["store"]
                    val addressId = nameToId["address"]
                    val cityId = nameToId["city"]
                    val countryId = nameToId["country"]
                    return@single caseNotion.edges.size == 3 &&
                            caseNotion.edges.any { edge -> edge.sourceClassId == storeId && edge.targetClassId == addressId } &&
                            caseNotion.edges.any { edge -> edge.sourceClassId == addressId && edge.targetClassId == cityId } &&
                            caseNotion.edges.any { edge -> edge.sourceClassId == cityId && edge.targetClassId == countryId }
                }

                currentEtlProcess = post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(
                    AbstractEtlProcess(
                        name = "autosakila",
                        dataConnectorId = currentDataConnector?.id,
                        isActive = true,
                        type = EtlProcessType.automatic,
                        caseNotion = caseNotion
                    )
                ) {
                    assertEquals(HttpStatusCode.Created, status)
                    return@post body<AbstractEtlProcess>()
                }

                // A delay for Debezium to kick in and start monitoring the DB
                Thread.sleep(5_000)

                sakila.populate()

                val info = runBlocking {
                    for (i in 0..30) {
                        val info = get<Paths.EtlProcess, EtlProcessInfo> {
                            return@get body<EtlProcessInfo>()
                        }
                        if (info.lastExecutionTime !== null)
                            return@runBlocking info
                        delay(1000)
                    }
                    error("The log was not generated in the prescribed amount of time")
                }
                assertTrue { info.errors.isNullOrEmpty() }
                val logIdentityId = info.logIdentityId

                val logs = runBlocking {
                    var previousNEvents = -1
                    for (i in 0..60) {
                        val stream: XMLXESInputStream = pqlQueryXES("where log:identity:id=$logIdentityId")
                        val logs = HoneyBadgerHierarchicalXESInputStream(stream)
                        val nEvents = logs.sumOf { log -> log.traces.sumOf { trace -> trace.events.count() } }
                        if (previousNEvents == nEvents)
                            return@runBlocking logs
                        previousNEvents = nEvents
                        delay(1_000)
                    }
                    error("The number of components in the log did not stabilize in the prescribed amount of time")
                }

                assertEquals(1, logs.count())
                val traces = logs.single().traces.toList()
                // There are 603 addresses in the DB + the city of London in Canada, which has no address assigned to it
                assertEquals(603 + 1, traces.size)
                for (t in traces) {
                    val events: List<Event> = t.events.toList()
                    assertEquals(1, events.count { "db:country" in it.attributes })
                    val country = events.firstNotNullOf { it.attributes.getOrNull("db:country") }.toString()
                    assertEquals(1, events.count { "db:city" in it.attributes })
                    val city = events.firstNotNullOf { it.attributes.getOrNull("db:city") }.toString()
                    val nAddresses = if (country == "\"Canada\"" && city == "\"London\"") 0 else 1
                    assertEquals(nAddresses, events.count { "db:address" in it.attributes })
                    val nStores = if (nAddresses > 0) {
                        val address = events.firstNotNullOf { it.attributes.getOrNull("db:address") }.toString()
                        if (address == "\"47 MySakila Drive\"" || address == "\"28 MySQL Boulevard\"") 1 else 0
                    } else 0
                    assertEquals(nStores, events.count { "db:store_id" in it.attributes })
                    assertEquals(2 + nAddresses + nStores, events.size)
                }
            }
    }

    @Test
    fun `complete workflow for automatic ETL process with Sakila on Postgres`(): Unit =
        PostgreSQLEnvironment(
            "sakila",
            "postgres",
            "sakila_password",
            PostgreSQLEnvironment.SAKILA_SCHEMA_SCRIPT,
            null
        ).use { sakila ->
            `complete workflow for automatic ETL process with Sakila`(sakila) {
                connect().use { connection ->
                    connection.autoCommit = false
                    connection.createStatement().use { s ->
                        s.execute(
                            File(
                                DBMSEnvironment.TEST_DATABASES_PATH,
                                PostgreSQLEnvironment.SAKILA_INSERT_SCRIPT
                            ).readText()
                        )
                    }
                    connection.commit()
                }
            }
        }

    @Test
    fun `complete workflow for automatic ETL process with Sakila on MySQL`(): Unit {
        val container = MySQLEnvironment.createContainer()
        Startables.deepStart(listOf(container)).join()
        MySQLEnvironment(container, "sakila").use { sakila ->
            sakila.configure(listOf(MySQLEnvironment.SAKILA_SCHEMA_SCRIPT))
            `complete workflow for automatic ETL process with Sakila`(sakila) {
                sakila.configure(listOf(MySQLEnvironment.SAKILA_INSERT_SCRIPT))
            }
        }
    }

    @Test
    fun `complete workflow for automatic ETL process with Sakila on MSSQL`(): Unit {
        val container = MSSQLEnvironment.createContainer()
        Startables.deepStart(listOf(container)).join()
        MSSQLEnvironment(container, "sakila").use { sakila ->
            sakila.configureWithScripts(MSSQLEnvironment.SAKILA_SCHEMA_SCRIPT, null)
            `complete workflow for automatic ETL process with Sakila`(sakila) {
                sakila.configureWithScripts(null, MSSQLEnvironment.SAKILA_INSERT_SCRIPT)
            }
        }
    }

    /**
     * The numbers in the test are computed by the test itself and are supposed only to prevent regressions/unexpected changes
     */
    @OptIn(InMemoryXESProcessing::class)
    private fun <T : DBMSEnvironment<*>> `complete workflow for multiple automatic ETL processes with Sakila`(
        sakila: T,
        populate: T.() -> Unit
    ) {
        ProcessMTestingEnvironment().withTemporaryDebeziumStorage().withFreshDatabase()
            .withProperty("processm.logs.limit.trace", "3000").run {
                registerUser("test@example.com", "some organization")
                login("test@example.com", "P@ssw0rd!")
                currentOrganizationId = organizations.single().id
                currentDataStore = createDataStore("datastore")
                post<Paths.ConnectionTest, DataConnector, Unit>(DataConnector(properties = sakila.connectionProperties)) {
                    assertEquals(HttpStatusCode.NoContent, status)
                }
                currentDataConnector = createDataConnector("dc1", sakila.connectionProperties)
                val relationshipGraph = get<Paths.RelationshipGraph, CaseNotion> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<CaseNotion>()
                }
                assertTrue { relationshipGraph.classes.isNotEmpty() }
                assertTrue { relationshipGraph.edges.isNotEmpty() }
                val caseNotions = get<Paths.CaseNotionSuggestions, Array<CaseNotion>> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<Array<CaseNotion>>()
                }
                assertTrue { caseNotions.isNotEmpty() }

                // The selection of tables is pretty boring, but other (e.g., film, rental, payment) are too big and the process takes unreasonably long for a test
                val expectedCaseNotions = listOf(
                    Triple(
                        setOf("city", "country", "address", "customer"),
                        setOf("city" to "country", "address" to "city", "customer" to "address"),
                        604
                    ),
                    Triple(
                        setOf("city", "country", "address", "store"),
                        setOf("city" to "country", "address" to "city", "store" to "address"),
                        604
                    ),
                    Triple(
                        setOf("staff", "store", "customer"),
                        setOf("staff" to "store", "customer" to "store"),
                        599
                    ),
                )

                val relevantCaseNotions = expectedCaseNotions.map { (expectedClasses, expectedEdges, _) ->
                    caseNotions.single { caseNotion ->
                        if (caseNotion.classes.values.toSet() != expectedClasses)
                            return@single false
                        if (caseNotion.edges.size != expectedEdges.size)
                            return@single false
                        return@single caseNotion.edges.all { edge ->
                            val namedEdge1 =
                                caseNotion.classes[edge.sourceClassId] to caseNotion.classes[edge.targetClassId]
                            val namedEdge2 = namedEdge1.second to namedEdge1.first
                            return@all namedEdge1 in expectedEdges || namedEdge2 in expectedEdges
                        }
                    }
                }

                val etlProcesses = relevantCaseNotions.map { caseNotion ->
                    post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(
                        AbstractEtlProcess(
                            name = "autosakila",
                            dataConnectorId = currentDataConnector?.id,
                            isActive = true,
                            type = EtlProcessType.automatic,
                            caseNotion = caseNotion
                        )
                    ) {
                        assertEquals(HttpStatusCode.Created, status)
                        return@post body<AbstractEtlProcess>()
                    }
                }

                // A delay for Debezium to kick in and start monitoring the DB
                Thread.sleep(5_000)

                sakila.populate()

                val infos = etlProcesses.parallelMap { etlProcess ->
                    for (i in 0..60) {
                        val info = get<Paths.EtlProcess, EtlProcessInfo>(etlProcess) {
                            return@get body<EtlProcessInfo>()
                        }
                        if (info.lastExecutionTime !== null)
                            return@parallelMap info
                        delay(1000)
                    }
                    error("The log was not generated in the prescribed amount of time")
                }

                for (info in infos)
                    assertTrue { info.errors.isNullOrEmpty() }

                assertEquals(etlProcesses.size, infos.size)
                for (i in 1 until etlProcesses.size)
                    assertNotEquals(infos[0].logIdentityId, infos[i].logIdentityId)

                val logs = infos.parallelMap { info ->
                    var previousNEvents = -1
                    for (i in 0..60) {
                        val stream = pqlQueryXES("where log:identity:id=${info.logIdentityId}")
                        val logs = HoneyBadgerHierarchicalXESInputStream(stream)
                        val nEvents = logs.sumOf { log -> log.traces.sumOf { trace -> trace.events.count() } }
                        if (previousNEvents == nEvents)
                            return@parallelMap logs.toList()
                        previousNEvents = nEvents
                        delay(1_000)
                    }
                    error("The number of components in the log did not stabilize in the prescribed amount of time")
                }.flatten()

                for ((log, expectedCaseNotion) in logs zip expectedCaseNotions) {
                    assertEquals(log.traces.count(), expectedCaseNotion.third)
                }
            }
    }

    @Test
    fun `complete workflow for multiple automatic ETL processes with Sakila on Postgres`(): Unit =
        PostgreSQLEnvironment(
            "sakila",
            "postgres",
            "sakila_password",
            PostgreSQLEnvironment.SAKILA_SCHEMA_SCRIPT,
            null
        ).use { sakila ->
            `complete workflow for multiple automatic ETL processes with Sakila`(sakila) {
                connect().use { connection ->
                    connection.autoCommit = false
                    connection.createStatement().use { s ->
                        s.execute(
                            File(
                                DBMSEnvironment.TEST_DATABASES_PATH,
                                PostgreSQLEnvironment.SAKILA_INSERT_SCRIPT
                            ).readText()
                        )
                    }
                    connection.commit()
                }
            }
        }

    @OptIn(InMemoryXESProcessing::class)
    @Test
    fun `two complete workflows for automatic ETL process with Sakila on the same datastore - Postgres and MySQL`() {
        PostgreSQLEnvironment(
            "sakila",
            "postgres",
            "sakila_password",
            PostgreSQLEnvironment.SAKILA_SCHEMA_SCRIPT,
            null
        ).use { postgresSakila ->
            val container = MySQLEnvironment.createContainer()
            Startables.deepStart(listOf(container)).join()
            MySQLEnvironment(container, "sakila").use { mysqlSakila ->
                mysqlSakila.configure(listOf(MySQLEnvironment.SAKILA_SCHEMA_SCRIPT))
                ProcessMTestingEnvironment().withTemporaryDebeziumStorage().withFreshDatabase()
                    .withProperty("processm.logs.limit.trace", "3000").run {
                        registerUser("test@example.com", "some organization")
                        login("test@example.com", "P@ssw0rd!")
                        currentOrganizationId = organizations.single().id
                        currentDataStore = createDataStore("datastore")
                        val postgresDC = createDataConnector("dc1", postgresSakila.connectionProperties)
                        val mysqlDC = createDataConnector("dc1", mysqlSakila.connectionProperties)
                        fun prepare(dc: DataConnector): AbstractEtlProcess {
                            currentDataConnector = dc
                            val caseNotion = get<Paths.CaseNotionSuggestions, Array<CaseNotion>> {
                                assertEquals(HttpStatusCode.OK, status)
                                return@get body<Array<CaseNotion>>()
                            }.single { caseNotion ->
                                if (caseNotion.classes.values.toSet() != setOf("city", "country", "address", "store"))
                                    return@single false
                                val nameToId = caseNotion.classes.inverse()
                                val storeId = nameToId["store"]
                                val addressId = nameToId["address"]
                                val cityId = nameToId["city"]
                                val countryId = nameToId["country"]
                                return@single caseNotion.edges.size == 3 &&
                                        caseNotion.edges.any { edge -> edge.sourceClassId == storeId && edge.targetClassId == addressId } &&
                                        caseNotion.edges.any { edge -> edge.sourceClassId == addressId && edge.targetClassId == cityId } &&
                                        caseNotion.edges.any { edge -> edge.sourceClassId == cityId && edge.targetClassId == countryId }
                            }

                            return post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(
                                AbstractEtlProcess(
                                    name = "autosakila",
                                    dataConnectorId = dc.id,
                                    isActive = true,
                                    type = EtlProcessType.automatic,
                                    caseNotion = caseNotion
                                )
                            ) {
                                assertEquals(HttpStatusCode.Created, status)
                                return@post body<AbstractEtlProcess>()
                            }
                        }

                        val etlProcesses = listOf(prepare(postgresDC), prepare(mysqlDC))
                        currentDataConnector = null

                        // A delay for Debezium to kick in and start monitoring the DB
                        Thread.sleep(5_000)

                        postgresSakila.connect().use { connection ->
                            connection.autoCommit = false
                            connection.createStatement().use { s ->
                                s.execute(
                                    File(
                                        DBMSEnvironment.TEST_DATABASES_PATH,
                                        PostgreSQLEnvironment.SAKILA_INSERT_SCRIPT
                                    ).readText()
                                )
                            }
                            connection.commit()
                        }
                        mysqlSakila.configure(listOf(MySQLEnvironment.SAKILA_INSERT_SCRIPT))

                        val infos = etlProcesses.parallelMap {
                            for (i in 0..10) {
                                val info = get<Paths.EtlProcess, EtlProcessInfo>(it) {
                                    return@get body<EtlProcessInfo>()
                                }
                                if (info.lastExecutionTime !== null) {
                                    return@parallelMap info
                                }
                                delay(1000)
                            }
                            error("The log was not generated in the prescribed amount of time")
                        }
                        assertEquals(2, infos.size)
                        assertNotEquals(infos[0].logIdentityId, infos[1].logIdentityId)


                        val logs = infos.parallelMap { info ->
                            var previousNEvents = -1
                            for (i in 0..60) {
                                val stream = pqlQueryXES("where log:identity:id=${info.logIdentityId}")
                                val logs = HoneyBadgerHierarchicalXESInputStream(stream)
                                val nEvents = logs.sumOf { log -> log.traces.sumOf { trace -> trace.events.count() } }
                                if (previousNEvents == nEvents)
                                    return@parallelMap logs.toList()
                                previousNEvents = nEvents
                                delay(1_000)
                            }
                            error("The number of components in the log did not stabilize in the prescribed amount of time")
                        }.flatten()

                        for (info in infos) {
                            assertTrue { info.errors.isNullOrEmpty() }
                        }

                        assertEquals(2, logs.size)

                        for (log in logs) {
                            val traces = log.traces.toList()
                            // There are 603 addresses in the DB + the city of London in Canada, which has no address assigned to it
                            assertEquals(603 + 1, traces.size)
                            for (t in traces) {
                                val events: List<Event> = t.events.toList()
                                assertEquals(1, events.count { "db:country" in it.attributes })
                                val country = events.firstNotNullOf { it.attributes.getOrNull("db:country") }.toString()
                                assertEquals(1, events.count { "db:city" in it.attributes })
                                val city = events.firstNotNullOf { it.attributes.getOrNull("db:city") }.toString()
                                val nAddresses = if (country == "\"Canada\"" && city == "\"London\"") 0 else 1
                                assertEquals(nAddresses, events.count { "db:address" in it.attributes })
                                val nStores = if (nAddresses > 0) {
                                    val address =
                                        events.firstNotNullOf { it.attributes.getOrNull("db:address") }.toString()
                                    if (address == "\"47 MySakila Drive\"" || address == "\"28 MySQL Boulevard\"") 1 else 0
                                } else 0
                                assertEquals(nStores, events.count { "db:store_id" in it.attributes })
                                assertEquals(2 + nAddresses + nStores, events.size)
                            }
                        }
                    }
            }
        }
    }
}