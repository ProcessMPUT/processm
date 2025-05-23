package processm

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.server.locations.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.javatime.CurrentDateTime
import org.junit.jupiter.api.Assumptions
import org.testcontainers.lifecycle.Startables
import processm.core.Brand
import processm.core.log.Event
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.LogInputStream
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.ComponentTypeDto
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataStores
import processm.etl.DBMSEnvironment
import processm.etl.MSSQLEnvironment
import processm.etl.MySQLEnvironment
import processm.etl.PostgreSQLEnvironment
import processm.helpers.mapToSet
import processm.logging.loggedScope
import processm.logging.logger
import processm.services.api.Paths
import processm.services.api.defaultSampleSize
import processm.services.api.getCustomProperties
import processm.services.api.models.*
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.sql.DriverManager
import java.sql.Statement
import java.util.*
import java.util.concurrent.ForkJoinPool
import kotlin.test.*

fun <T, K, V> Array<T>.associateNotNull(transform: (T) -> Pair<K, V>?): Map<K, V> {
    val result = HashMap<K, V>()
    for (item in this)
        transform(item)?.let { result[it.first] = it.second }
    return result
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
            currentDataConnector =
                createDataConnector("dc1", ConnectionProperties(ConnectionType.JdbcString, jdbcUrl!!))
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
                assertTrue { none() }
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
            currentDataConnector =
                createDataConnector("dc1", ConnectionProperties(ConnectionType.JdbcString, sakilaJdbcUrl))

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

            val logs: LogInputStream = pqlQuery("where log:identity:id=$logIdentityId")

            assertEquals(1, logs.count())
            logs.first().let { log ->
                assertEquals(logIdentityId, log.identityId)
                val nItems = 1 + log.traces.count() + log.traces.sumOf { trace -> trace.events.count() }
                assertTrue { nItems <= defaultSampleSize }
            }

            deleteLog(logIdentityId)
            with(pqlQuery("where log:identity:id=$logIdentityId")) {
                assertTrue { none() }
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
    fun `complete workflow for resetting password`() = loggedScope { logger ->
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
                val startTime = System.currentTimeMillis()
                // do not remove the "kotlin." prefix, as otherwise ProcessMTestingEnvironment.run is invoked
                val token = kotlin.run {
                    for (i in 1..50) {
                        try {
                            re.find(sendmail.lastEmailBody)?.value?.let {
                                logger.info("E-mail sent in ${System.currentTimeMillis() - startTime}ms")
                                return@run it
                            }
                        } catch (e: IOException) {
                            logger.debug("E-mail has not been sent yet", e)
                        }
                        Thread.sleep(50L)
                    }
                    error("E-mail was not sent in time limit.")
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
        defaultSchema: String?,
        populate: T.() -> Unit
    ) {
        val wrap: (String) -> String =
            if (defaultSchema == null) { className -> className } else { className -> "$defaultSchema.$className" }
        ProcessMTestingEnvironment()
            .withTemporaryDebeziumStorage()
            .withFreshDatabase()
            .withProperty("processm.logs.limit.trace", "3000")
            .run {
                registerUser("test@example.com", "some organization")
                login("test@example.com", "P@ssw0rd!")
                currentOrganizationId = organizations.single().id
                currentDataStore = createDataStore("datastore")
                post<Paths.ConnectionTest, DataConnector, Unit>(DataConnector(connectionProperties = sakila.connectionProperties)) {
                    assertEquals(HttpStatusCode.NoContent, status)
                }
                currentDataConnector = createDataConnector("dc1", sakila.connectionProperties)
                val relationshipGraph = get<Paths.RelationshipGraph, RelationshipGraph> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<RelationshipGraph>()
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

                val classNames = setOf(wrap("city"), wrap("country"), wrap("address"), wrap("store"))
                val classes = relationshipGraph.classes.associateNotNull {
                    if (it.name in classNames) it.name to it.id
                    else null
                }
                assertEquals(4, classes.size)
                val storeId = classes[wrap("store")]!!
                val addressId = classes[wrap("address")]!!
                val cityId = classes[wrap("city")]!!
                val countryId = classes[wrap("country")]!!
                val expectedEdges = relationshipGraph.edges.mapNotNullTo(HashSet()) { edge ->
                    if ((edge.sourceClassId == storeId && edge.targetClassId == addressId) ||
                        (edge.sourceClassId == addressId && edge.targetClassId == cityId) ||
                        (edge.sourceClassId == cityId && edge.targetClassId == countryId)
                    ) edge.id
                    else null
                }
                val expectedClassIds = classes.values.toSet()

                //Find the following CaseNotion to ensure the rest of the test works correctly
                //CaseNotion(classes={11=city, 5=country, 4=address, 21=store}, edges=[CaseNotionEdgesInner(sourceClassId=11, targetClassId=5), CaseNotionEdgesInner(sourceClassId=4, targetClassId=11), CaseNotionEdgesInner(sourceClassId=21, targetClassId=4)])
                val caseNotion = caseNotions.single { caseNotion ->
                    (caseNotion.classes.toSet() == expectedClassIds) && (caseNotion.edges.toSet() == expectedEdges)
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
                    var counter = 0
                    for (i in 0..60) {
                        val stream: XMLXESInputStream = pqlQueryXES("where log:identity:id=$logIdentityId")
                        val logs = HoneyBadgerHierarchicalXESInputStream(stream)
                        val nEvents = logs.sumOf { log -> log.traces.sumOf { trace -> trace.events.count() } }
                        if (previousNEvents == nEvents) {
                            counter += 1
                            if (counter == 5)
                                return@runBlocking logs
                        } else {
                            counter = 0
                            previousNEvents = nEvents
                        }
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
            `complete workflow for automatic ETL process with Sakila`(sakila, "public") {
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
            `complete workflow for automatic ETL process with Sakila`(sakila, null) {
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
            `complete workflow for automatic ETL process with Sakila`(sakila, "dbo") {
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
        defaultSchema: String?,
        populate: T.() -> Unit
    ) {
        val wrap: (String) -> String =
            if (defaultSchema == null) { className -> className } else { className -> "$defaultSchema.$className" }
        ProcessMTestingEnvironment().withTemporaryDebeziumStorage().withFreshDatabase()
            .withProperty("processm.logs.limit.trace", "3000").run {
                registerUser("test@example.com", "some organization")
                login("test@example.com", "P@ssw0rd!")
                currentOrganizationId = organizations.single().id
                currentDataStore = createDataStore("datastore")
                post<Paths.ConnectionTest, DataConnector, Unit>(DataConnector(connectionProperties = sakila.connectionProperties)) {
                    assertEquals(HttpStatusCode.NoContent, status)
                }
                currentDataConnector = createDataConnector("dc1", sakila.connectionProperties)
                val relationshipGraph = get<Paths.RelationshipGraph, RelationshipGraph> {
                    assertEquals(HttpStatusCode.OK, status)
                    return@get body<RelationshipGraph>()
                }
                assertTrue { relationshipGraph.classes.isNotEmpty() }
                assertTrue { relationshipGraph.edges.isNotEmpty() }
                val classNameToId = relationshipGraph.classes.associate { it.name to it.id }
                val classesToRelationships =
                    relationshipGraph.edges.associate { (it.sourceClassId to it.targetClassId) to it.id }
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
                ).map {
                    Triple(
                        it.first.mapToSet { classNameToId[wrap(it)]!! },
                        it.second.mapToSet {
                            classesToRelationships[classNameToId[wrap(it.first)]!! to classNameToId[wrap(it.second)]!!]!!
                        },
                        it.third
                    )
                }

                val etlProcesses = expectedCaseNotions.map { caseNotion ->
                    post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(
                        AbstractEtlProcess(
                            name = "autosakila",
                            dataConnectorId = currentDataConnector?.id,
                            isActive = true,
                            type = EtlProcessType.automatic,
                            caseNotion = CaseNotion(caseNotion.first.toTypedArray(), caseNotion.second.toTypedArray())
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
                    for (i in 0..90) {
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
            `complete workflow for multiple automatic ETL processes with Sakila`(sakila, "public") {
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
                        fun prepare(dc: DataConnector, wrap: (String) -> String): AbstractEtlProcess {
                            currentDataConnector = dc

                            val relationshipGraph = get<Paths.RelationshipGraph, RelationshipGraph> {
                                assertEquals(HttpStatusCode.OK, status)
                                return@get body<RelationshipGraph>()
                            }
                            val classNames = setOf(wrap("city"), wrap("country"), wrap("address"), wrap("store"))
                            val classes = relationshipGraph.classes.associateNotNull {
                                if (it.name in classNames) it.name to it.id
                                else null
                            }
                            val storeId = classes[wrap("store")]!!
                            val addressId = classes[wrap("address")]!!
                            val cityId = classes[wrap("city")]!!
                            val countryId = classes[wrap("country")]!!
                            val expectedEdges = relationshipGraph.edges.mapNotNullTo(HashSet()) { edge ->
                                if ((edge.sourceClassId == storeId && edge.targetClassId == addressId) ||
                                    (edge.sourceClassId == addressId && edge.targetClassId == cityId) ||
                                    (edge.sourceClassId == cityId && edge.targetClassId == countryId)
                                ) edge.id
                                else null
                            }
                            val expectedClassIds = classes.values.toSet()

                            val caseNotion = get<Paths.CaseNotionSuggestions, Array<CaseNotion>> {
                                assertEquals(HttpStatusCode.OK, status)
                                return@get body<Array<CaseNotion>>()
                            }.single { caseNotion ->
                                caseNotion.classes.toSet() == expectedClassIds && caseNotion.edges.toSet() == expectedEdges
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

                        val etlProcesses = listOf(prepare(postgresDC) { "public.$it" }, prepare(mysqlDC) { it })
                        currentDataConnector = null

                        // A delay for Debezium to kick in and start monitoring the DB
                        Thread.sleep(5_000)

                        val postgresInsertTask = ForkJoinPool.commonPool().submit {
                            val stime = System.currentTimeMillis()
                            postgresSakila.connect().use { connection ->
                                connection.autoCommit = false
                                connection.createStatement().use { s ->
                                    s.execute(
                                        File(
                                            DBMSEnvironment.TEST_DATABASES_PATH,
                                            PostgreSQLEnvironment.SAKILA_INSERT_SCRIPT
                                        ).readText(),
                                        Statement.NO_GENERATED_KEYS
                                    )
                                }
                                connection.commit()
                            }
                            logger().info("Data inserted to Postgres in ${System.currentTimeMillis() - stime}ms")
                        }
                        val mysqlInsertTask = ForkJoinPool.commonPool().submit {
                            val stime = System.currentTimeMillis()
                            mysqlSakila.configure(listOf(MySQLEnvironment.SAKILA_INSERT_SCRIPT))
                            logger().info("Data inserted to MySQL in ${System.currentTimeMillis() - stime}ms")
                        }
                        postgresInsertTask.join()
                        mysqlInsertTask.join()

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

    @Test
    fun `how many data stores is too many for ProcessM`() {
        var jdbcUrl = ""
        val email = "user@example.com"
        ProcessMTestingEnvironment().withFreshDatabase().run {
            jdbcUrl = this.jdbcUrl!!
            registerUser(email, "org")
            login(email, "P@ssw0rd!")
            transactionMain {
                DataStores.batchInsert(1..100) { i ->
                    this[DataStores.name] = "ds$i"
                    this[DataStores.creationDate] = CurrentDateTime
                }
            }
        }
        ProcessMTestingEnvironment().withPreexistingDatabase(jdbcUrl).run {
            login(email, "P@ssw0rd!")
        }
    }

    class ProcessSimulator(val jdbcUrl: String, val tableName: String) {
        private var eventId = 0
        private var traceId = 0

        init {
            DriverManager.getConnection(jdbcUrl!!).use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute(
                        """
                        CREATE TABLE $tableName (
    trace_id integer NOT NULL,
    event_id integer NOT NULL primary key ,
    payload text NOT NULL
);
                    """.trimIndent()
                    )
                }
            }
        }

        fun insert(log: List<List<String>>) {
            val query = buildString {
                append("insert into $tableName(trace_id, event_id, payload) values ")
                repeat(log.sumOf { it.size }) {
                    append("(?, ?, ?),")
                }
                deleteCharAt(length - 1)
            }
            DriverManager.getConnection(jdbcUrl).use { connection ->
                connection.prepareStatement(query).use { stmt ->
                    var varId = 0
                    for (trace in log) {
                        traceId++
                        for (event in trace) {
                            eventId++
                            varId += 1
                            stmt.setInt(varId, traceId)
                            varId += 1
                            stmt.setInt(varId, eventId)
                            varId += 1
                            stmt.setString(varId, event)
                        }
                    }
                    stmt.executeUpdate()
                }
            }
        }
    }

    @Test
    fun `manual ETL with concept drift`() {
        val etlProcessName = "concept drifting"

        val tableName = "manualETL76b7c90b"

        val query = """
            SELECT event_id, trace_id, payload FROM $tableName
 WHERE event_id > CAST(? AS bigint)
    """.trimIndent()

        ProcessMTestingEnvironment().withFreshDatabase().withAlignerDelay(100L).run {
            val simulator = ProcessSimulator(jdbcUrl!!, tableName)

            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector =
                createDataConnector("dc1", ConnectionProperties(ConnectionType.JdbcString, jdbcUrl!!))

            val initialDefinition = AbstractEtlProcess(
                name = etlProcessName,
                dataConnectorId = currentDataConnector?.id!!,
                type = EtlProcessType.jdbc,
                configuration = JdbcEtlProcessConfiguration(
                    query,
                    false,
                    false,
                    JdbcEtlColumnConfiguration("trace_id", "trace_id"),
                    JdbcEtlColumnConfiguration("event_id", "event_id"),
                    arrayOf(JdbcEtlColumnConfiguration("payload", "concept:name")),
                    lastEventExternalId = "0"
                )
            )

            currentEtlProcess = post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(initialDefinition) {
                return@post body<AbstractEtlProcess>()
            }.apply {
                assertEquals(etlProcessName, name)
                assertEquals(currentDataConnector?.id, dataConnectorId)
                assertNotNull(id)
            }

            simulator.insert(List(10) { listOf("a1", "a2") })

            post<Paths.EtlProcess, Unit, Unit>(null) {
                assertTrue { status.isSuccess() }
            }

            val logIdentityId = get<Paths.EtlProcess, UUID> {
                return@get body<EtlProcessInfo>().logIdentityId
            }

            currentWorkspaceId = post<Paths.Workspaces, NewWorkspace, UUID>(NewWorkspace("workspace")) {
                return@post body<Workspace>().id!!
            }

            val eventsCounter = Semaphore(5, 5)

            sse(format<Paths.Notifications>()) {
                eventsCounter.release()
            }

            assertEquals(0, eventsCounter.availablePermits)

            waitUntil {
                with(pqlQuery("where log:identity:id=$logIdentityId")) {
                    count() == 1 && single().traces.count() == 10
                }
            }

            currentComponentId = UUID.randomUUID()
            put<Paths.WorkspaceComponent, AbstractComponent, Unit>(
                AbstractComponent(
                    currentComponentId!!,
                    "where log:identity:id=$logIdentityId",
                    currentDataStore?.id!!,
                    ComponentType.causalNet,
                    getCustomProperties(ComponentTypeDto.CausalNet),
                    name = "test causalnet component"
                )
            ) {
                assertTrue { status.isSuccess() }
            }

            // first model + alignments
            eventsCounter.waitUntilAcquired(2)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            simulator.insert(List(5) { listOf("b1", "b2") })

            post<Paths.EtlProcess, Unit, Unit>(null) {
                assertTrue { status.isSuccess() }
            }

            // alignments for the old model + new model
            eventsCounter.waitUntilAcquired(2)

            val availableModelVersions = get<Paths.WorkspaceComponentData, List<Long>> {
                return@get body<List<Long>>()
            }

            assertEquals(2, availableModelVersions.size)

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.min().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(4, nodes.size)
            }

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.max().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(6, nodes.size)
            }

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            assertEquals(0, eventsCounter.availablePermits)

            patch<Paths.WorkspaceComponentData, Long, Unit>(availableModelVersions.max()) {
                assertTrue { status.isSuccess() }
            }

            // alignments for the new model
            eventsCounter.waitUntilAcquired(1)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(6, this?.nodes?.size)
                assertEquals(15, this?.alignmentKPIReport?.alignments?.size)
            }
        }
    }

    @Test
    fun `JDBC ETL with concept drift - component before the data`() {
        val etlProcessName = "concept drifting"

        val tableName = "manualETLfc9d56fe"

        val query = """
            SELECT event_id, trace_id, payload FROM $tableName
 WHERE event_id > CAST(? AS bigint)
    """.trimIndent()

        ProcessMTestingEnvironment().withFreshDatabase().withAlignerDelay(100L).run {

            val simulator = ProcessSimulator(jdbcUrl!!, tableName)

            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector =
                createDataConnector("dc1", ConnectionProperties(ConnectionType.JdbcString, jdbcUrl!!))

            val initialDefinition = AbstractEtlProcess(
                name = etlProcessName,
                dataConnectorId = currentDataConnector?.id!!,
                type = EtlProcessType.jdbc,
                configuration = JdbcEtlProcessConfiguration(
                    query,
                    false,
                    false,
                    JdbcEtlColumnConfiguration("trace_id", "trace_id"),
                    JdbcEtlColumnConfiguration("event_id", "event_id"),
                    arrayOf(JdbcEtlColumnConfiguration("payload", "concept:name")),
                    lastEventExternalId = "0"
                )
            )

            currentEtlProcess = post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(initialDefinition) {
                return@post body<AbstractEtlProcess>()
            }.apply {
                assertEquals(etlProcessName, name)
                assertEquals(currentDataConnector?.id, dataConnectorId)
                assertNotNull(id)
            }

            val logIdentityId = get<Paths.EtlProcess, UUID> {
                return@get body<EtlProcessInfo>().logIdentityId
            }

            currentWorkspaceId = post<Paths.Workspaces, NewWorkspace, UUID>(NewWorkspace("workspace")) {
                return@post body<Workspace>().id!!
            }


            val eventsCounter = Semaphore(6, 6)

            sse(format<Paths.Notifications>()) {
                eventsCounter.release()
            }

            currentComponentId = UUID.randomUUID()
            put<Paths.WorkspaceComponent, AbstractComponent, Unit>(
                AbstractComponent(
                    currentComponentId!!,
                    "where log:identity:id=$logIdentityId",
                    currentDataStore?.id!!,
                    ComponentType.causalNet,
                    getCustomProperties(ComponentTypeDto.CausalNet),
                    name = "test causalnet component"
                )
            ) {
                assertTrue { status.isSuccess() }
            }

            // error due to no data
            eventsCounter.waitUntilAcquired(1)

            simulator.insert(List(10) { listOf("a1", "a2") })

            post<Paths.EtlProcess, Unit, Unit>(null) {
                assertTrue { status.isSuccess() }
            }

            waitUntil {
                with(pqlQuery("where log:identity:id=$logIdentityId")) {
                    count() == 1 && single().traces.count() == 10
                }
            }

            // first model + alignments
            eventsCounter.waitUntilAcquired(2)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            simulator.insert(List(5) { listOf("b1", "b2") })

            post<Paths.EtlProcess, Unit, Unit>(null) {
                assertTrue { status.isSuccess() }
            }

            // alignments for the old model + new model
            eventsCounter.waitUntilAcquired(2)

            val availableModelVersions = get<Paths.WorkspaceComponentData, List<Long>> {
                return@get body<List<Long>>()
            }

            assertEquals(2, availableModelVersions.size)

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.min().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(4, nodes.size)
            }

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.max().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(6, nodes.size)
            }

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            patch<Paths.WorkspaceComponentData, Long, Unit>(availableModelVersions.max()) {
                assertTrue { status.isSuccess() }
            }

            // alignments for the new model
            eventsCounter.waitUntilAcquired(1)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(6, this?.nodes?.size)
                assertEquals(15, this?.alignmentKPIReport?.alignments?.size)
            }
        }
    }

    @Test
    fun `JDBC ETL - aggregating multiple changes`() {
        val etlProcessName = "concept drifting"

        val tableName = "manualETL76b7c90b"

        val query = """
            SELECT event_id, trace_id, payload FROM $tableName
 WHERE event_id > CAST(? AS bigint)
    """.trimIndent()

        ProcessMTestingEnvironment().withFreshDatabase().withAlignerDelay(500L).run {
            val simulator = ProcessSimulator(jdbcUrl!!, tableName)

            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            currentDataConnector =
                createDataConnector("dc1", ConnectionProperties(ConnectionType.JdbcString, jdbcUrl!!))

            val initialDefinition = AbstractEtlProcess(
                name = etlProcessName,
                dataConnectorId = currentDataConnector?.id!!,
                type = EtlProcessType.jdbc,
                configuration = JdbcEtlProcessConfiguration(
                    query,
                    false,
                    false,
                    JdbcEtlColumnConfiguration("trace_id", "trace_id"),
                    JdbcEtlColumnConfiguration("event_id", "event_id"),
                    arrayOf(JdbcEtlColumnConfiguration("payload", "concept:name")),
                    lastEventExternalId = "0"
                )
            )

            currentEtlProcess = post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(initialDefinition) {
                return@post body<AbstractEtlProcess>()
            }.apply {
                assertEquals(etlProcessName, name)
                assertEquals(currentDataConnector?.id, dataConnectorId)
                assertNotNull(id)
            }

            simulator.insert(List(10) { listOf("a1", "a2") })

            post<Paths.EtlProcess, Unit, Unit>(null) {
                assertTrue { status.isSuccess() }
            }

            val logIdentityId = get<Paths.EtlProcess, UUID> {
                return@get body<EtlProcessInfo>().logIdentityId
            }

            currentWorkspaceId = post<Paths.Workspaces, NewWorkspace, UUID>(NewWorkspace("workspace")) {
                return@post body<Workspace>().id!!
            }

            val eventsCounter = Semaphore(5, 5)

            sse(format<Paths.Notifications>()) {
                eventsCounter.release()
            }

            assertEquals(0, eventsCounter.availablePermits)

            waitUntil {
                with(pqlQuery("where log:identity:id=$logIdentityId")) {
                    count() == 1 && single().traces.count() == 10
                }
            }

            currentComponentId = UUID.randomUUID()
            put<Paths.WorkspaceComponent, AbstractComponent, Unit>(
                AbstractComponent(
                    currentComponentId!!,
                    "where log:identity:id=$logIdentityId",
                    currentDataStore?.id!!,
                    ComponentType.causalNet,
                    getCustomProperties(ComponentTypeDto.CausalNet),
                    name = "test causalnet component"
                )
            ) {
                assertTrue { status.isSuccess() }
            }

            // first model + alignments
            eventsCounter.waitUntilAcquired(2)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            repeat(5) {
                simulator.insert(listOf(listOf("b1", "b2")))

                post<Paths.EtlProcess, Unit, Unit>(null) {
                    assertTrue { status.isSuccess() }
                }
            }

            // alignments for the old model + new model
            eventsCounter.waitUntilAcquired(2)

            val availableModelVersions = get<Paths.WorkspaceComponentData, List<Long>> {
                return@get body<List<Long>>()
            }

            assertEquals(2, availableModelVersions.size)

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.min().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(4, nodes.size)
            }

            get(format<Paths.WorkspaceComponentDataVariant>("variantId" to availableModelVersions.max().toString())) {
                return@get body<CausalNetComponentData>()
            }.apply {
                assertEquals(6, nodes.size)
            }

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(4, this?.nodes?.size)
            }

            assertEquals(0, eventsCounter.availablePermits)

            patch<Paths.WorkspaceComponentData, Long, Unit>(availableModelVersions.max()) {
                assertTrue { status.isSuccess() }
            }

            // alignments for the new model
            eventsCounter.waitUntilAcquired(1)

            get<Paths.WorkspaceComponent, CausalNetComponentData?> {
                return@get body<AbstractComponent>().data as CausalNetComponentData?
            }.apply {
                assertEquals(6, this?.nodes?.size)
                assertEquals(15, this?.alignmentKPIReport?.alignments?.size)
            }
        }
    }

    @Test
    fun `uploading large XES file`() {
        ProcessMTestingEnvironment().withFreshDatabase().run {

            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")

            runBlocking {
                val response = client.post(apiUrl(format<Paths.Logs>())) {
                    token?.let { bearerAuth(it) }
                    setBody(MultiPartFormDataContent(formData {
                        append(
                            "fileName",
                            File("../xes-logs/data-driven_process_discovery-artificial_event_log-10-percent-noise.xes.gz").readBytes(),
                            Headers.build {
                                append(HttpHeaders.ContentType, ContentType.Application.GZip)
                                append(HttpHeaders.ContentDisposition, "filename=\"log.xes.gz\"")
                            })
                    }))
                }
                assertTrue { response.status.isSuccess() }
            }
        }
    }

    private fun ProcessMTestingEnvironment.aclTestHelper() {
        registerUser("test@example.com", "some organization")
        login("test@example.com", "P@ssw0rd!")
        currentWorkspaceId = post<Paths.Workspaces, NewWorkspace, UUID>(NewWorkspace("workspace")) {
            return@post body<Workspace>().id!!
        }
        get("acl/urn%3Aprocessm%3Adb%2Fworkspaces%2F${currentWorkspaceId}") {
            assertEquals(HttpStatusCode.OK, this.status)
        }
    }

    /**
     * Test for #332
     */
    @Test
    fun `ACL over HTTP2`() {
        ProcessMTestingEnvironment().withFreshDatabase().withHttp2().run { aclTestHelper() }
    }

    /**
     * Test for #332
     */
    @Test
    fun `ACL over HTTP1`() {
        ProcessMTestingEnvironment().withFreshDatabase().run { aclTestHelper() }
    }
}
