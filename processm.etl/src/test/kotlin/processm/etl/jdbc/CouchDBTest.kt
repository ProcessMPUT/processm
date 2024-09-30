package processm.etl.jdbc

import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgroups.util.UUID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.testcontainers.lifecycle.Startables
import processm.core.DBTestHelper
import processm.core.log.DBXESInputStream
import processm.core.log.Event
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.models.ConnectionProperties
import processm.dbmodels.models.ConnectionType
import processm.dbmodels.models.DataConnector
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.CouchDBContainer
import processm.etl.helpers.getConnection
import processm.etl.jdbc.nosql.CouchDBConnection
import processm.etl.toJsonElements
import java.sql.Types
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CouchDBTest {

    companion object {

        private val username = UUID.randomUUID().toString()
        private val password = UUID.randomUUID().toString()
        private lateinit var container: CouchDBContainer<*>

        @JvmStatic
        @BeforeAll
        fun setupContainer() {
            container = CouchDBContainer().withUsername(username).withPassword(password)
            Startables.deepStart(container).join()
        }

        @JvmStatic
        @AfterAll
        fun takeDownContainer() {
            container.close()
        }
    }

    private lateinit var dbName: String

    @BeforeTest
    fun setupTest() {
        dbName = "db" + UUID.randomUUID().toString().substring(8)
    }

    @Test
    fun `property-based data connector`() {
        transaction(DBCache.get(DBTestHelper.dbName).database) {
            DataConnector.new {
                name = "blah"
                connectionProperties = ConnectionProperties(
                    ConnectionType.CouchDBProperties,
                    server = container.host,
                    port = container.port,
                    username = username,
                    password = password,
                    database = ""
                )
            }.getConnection().use {
                (it as CouchDBConnection).createDatabase(dbName)
            }
            rollback()
        }
    }

    @Test
    fun journal() {
        transaction(DBCache.get(DBTestHelper.dbName).database) {
            val connector = DataConnector.new {
                name = "blah"
                connectionProperties =
                    ConnectionProperties(ConnectionType.CouchDBString, """couchdb:${container.url}/$dbName""")
            }
            val etl = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    name = "Journal"
                    dataConnector = connector
                    processType = "jdbc"
                }
                query = """{"selector": {"attributes": {"event_id": {"${'$'}gt": ?}}}}"""
                lastEventExternalId = "-1"
                lastEventExternalIdType = Types.INTEGER
                batch = false
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "attributes.event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "attributes.trace_id"
                target = "trace_id"
                traceId = true
            }
            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "attributes.concept:name"
                target = "concept:name"
            }
            ETLColumnToAttributeMap.new {
                configuration = etl
                sourceColumn = "attributes.lifecycle:transition"
                target = "lifecycle:transition"
            }
            CouchDBConnection(container.url).use { conn ->
                conn.createDatabase(dbName)
            }
            CouchDBConnection("${container.url}/$dbName/").use { conn ->
                val input = DBXESInputStream(
                    DBTestHelper.dbName,
                    Query("where l:identity:id=${DBTestHelper.JournalReviewExtra}")
                ).toJsonElements().toList()
                for (start in input.indices step 100) {
                    val inputBatch = input.subList(start, start + 100)
                    conn.batchInsert(inputBatch.iterator())
                    val outputBatch = etl.toXESInputStream().toList()
                    val additional = when {
                        start == 0 -> 0
                        inputBatch[0].jsonObject["type"]?.jsonPrimitive?.content == "trace" -> 1
                        else -> 2
                    } - if (inputBatch.last().jsonObject["type"]?.jsonPrimitive?.content == "trace") 1 else 0
                    assertEquals(inputBatch.size + additional, outputBatch.size)
                    val inputEvents = inputBatch.filter { it["type"]?.jsonPrimitive?.content == "event" }
                    val outputEvents = outputBatch.filterIsInstance<Event>()
                    assertEquals(inputEvents.size, outputEvents.size)
                    assertTrue { (inputEvents zip outputEvents).all { (i, o) -> i["attributes"]!!.jsonObject["concept:name"]?.jsonPrimitive?.content == o.conceptName && i["attributes"]!!.jsonObject["lifecycle:transition"]?.jsonPrimitive?.content == o.lifecycleTransition } }
                }
            }
            rollback()
        }
    }
}