package processm

import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.locations.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assumptions
import processm.core.Brand
import processm.services.api.Paths
import processm.services.api.defaultSampleSize
import processm.services.api.models.*
import java.io.File
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
                Thread.sleep(100L)
                val re = Regex("[0-9A-F]{8}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{4}-[0-9A-F]{12}", RegexOption.IGNORE_CASE)
                val token = re.find(sendmail.lastEmailBody)?.value
                assertNotNull(token)
                post("/reset-password/$token", PasswordChange("", "newPassword")) {
                    assertEquals(HttpStatusCode.OK, status)
                }
                login("text@example.com", "newPassword")
            }
    }

    /**
    Numeric values in this test are computed by the test itself and are only intended to prevent unexpected change,
    not to verify correctness
     */
    @Test
    fun `complete workflow for automatic ETL process with Sakila on Postgres`() {
        ProcessMTestingEnvironment().withFreshDatabase().run {
            registerUser("test@example.com", "some organization")
            login("test@example.com", "P@ssw0rd!")
            currentOrganizationId = organizations.single().id
            currentDataStore = createDataStore("datastore")
            post<Paths.ConnectionTest, DataConnector, Unit>(DataConnector(properties = sakilaProperties)) {
                assertEquals(HttpStatusCode.NoContent, status)
            }
            currentDataConnector = createDataConnector("dc1", sakilaProperties)
            val relationshipGraph = get<Paths.RelationshipGraph, CaseNotion> {
                assertEquals(HttpStatusCode.OK, status)
                return@get body<CaseNotion>()
            }
            assertEquals(21, relationshipGraph.classes.size)
            assertEquals(39, relationshipGraph.edges.size)
            val caseNotions = get<Paths.CaseNotionSuggestions, Array<CaseNotion>> {
                assertEquals(HttpStatusCode.OK, status)
                return@get body<Array<CaseNotion>>()
            }
            assertEquals(354, caseNotions.size)
            currentEtlProcess = post<Paths.EtlProcesses, AbstractEtlProcess, AbstractEtlProcess>(
                AbstractEtlProcess(
                    name = "autosakila",
                    dataConnectorId = currentDataConnector?.id,
                    isActive = true,
                    type = EtlProcessType.automatic,
                    caseNotion = caseNotions[0]
                )
            ) {
                assertEquals(HttpStatusCode.Created, status)
                return@post body<AbstractEtlProcess>()
            }

            Thread.sleep(5_000)

            sakilaEnv.value.clearAllData()
            sakilaEnv.value.populate()

            Thread.sleep(60_000)


            //TODO I am confused. Do I need to call this? I thought this is an automatic ETL process?
            post<Paths.EtlProcessLog, Unit> {
                assertEquals(HttpStatusCode.NoContent, status)
            }

            val info = runBlocking {
                for (i in 0..60) {
                    val info = get<Paths.EtlProcess, EtlProcessInfo> {
                        return@get body<EtlProcessInfo>()
                    }
                    println("$i $info")
                    if (info.lastExecutionTime !== null)
                        return@runBlocking info
                    delay(1000)
                }
                error("The ETL process was not executed in the prescribed amount of time")
            }
            assertTrue { info.errors.isNullOrEmpty() }
            val logIdentityId = info.logIdentityId

            val logs: Array<Any> = pqlQuery("where log:identity:id=$logIdentityId")
            println(logs.size)
            //TODO verify actual size or something
            assertTrue { logs.isNotEmpty() }
        }
    }

}
