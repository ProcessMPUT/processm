package processm

import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.locations.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import processm.services.api.Paths
import processm.services.api.defaultSampleSize
import processm.services.api.models.*
import java.time.LocalDateTime
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
    return gsonBuilder.create().fromJson(body<String>(), T::class.java)
}

@OptIn(KtorExperimentalLocationsAPI::class)
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
                    delay(1000)
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
            assertThrows<ClientRequestException> {
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
            login("test@example.com", "pass")
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
            assertThrows<ClientRequestException> {
                get<Paths.EtlProcess, Unit> {}
            }
        }
    }

}
