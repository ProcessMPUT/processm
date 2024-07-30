package processm.etl.jdbc

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
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
import processm.dbmodels.models.DataConnector
import processm.dbmodels.models.EtlProcessMetadata
import processm.etl.MongoDBContainer
import processm.etl.helpers.getConnection
import processm.etl.jdbc.nosql.CouchDBConnection
import processm.etl.jdbc.nosql.MongoDBConnection
import processm.etl.toJsonElements
import java.sql.Types
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MongoDBTest {


    companion object {

        private val username = UUID.randomUUID().toString()
        private val password = UUID.randomUUID().toString()
        private lateinit var container: MongoDBContainer<*>

        @JvmStatic
        @BeforeAll
        fun setupContainer() {
            container = MongoDBContainer("mongo:7.0").withUsername(username).withPassword(password)
            Startables.deepStart(container).join()
        }

        @JvmStatic
        @AfterAll
        fun takeDownContainer() {
            container.close()
        }
    }


    private lateinit var dbName: String
    private lateinit var collectionName: String

    @BeforeTest
    fun setupTest() {
        dbName = "db" + UUID.randomUUID().toString().substring(8)
        collectionName = "col" + UUID.randomUUID().toString().substring(8)
    }


    @Test
    fun `property-based data connector`() {
        transaction(DBCache.get(DBTestHelper.dbName).database) {
            DataConnector.new {
                name = "blah"
                connectionProperties = buildJsonObject {
                    put("connection-type", JsonPrimitive("MongoDB"))
                    put("server", JsonPrimitive(container.host))
                    put("port", JsonPrimitive(container.port.toString()))
                    put("username", JsonPrimitive(username))
                    put("password", JsonPrimitive(password))
                    put("database", JsonPrimitive(dbName))
                    put("collection", JsonPrimitive(collectionName))
                }.toString()
            }.getConnection().use {
                it.prepareStatement("{}").use {
                    it.executeQuery()
                }
            }
            rollback()
        }
    }

    @Test
    fun journal() {
        transaction(DBCache.get(DBTestHelper.dbName).database) {
            val connector = DataConnector.new {
                name = "blah"
                connectionProperties = """${container.url}##$dbName##$collectionName"""
            }
            val etl = ETLConfiguration.new {
                metadata = EtlProcessMetadata.new {
                    name = "Journal"
                    dataConnector = connector
                    processType = "jdbc"
                }
                query = """{"attributes.event_id": {"${'$'}gt": ?}}"""
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
            MongoDBConnection(container.url, dbName, collectionName).use { conn ->
                val input = DBXESInputStream(
                    DBTestHelper.dbName,
                    Query("where l:identity:id=${DBTestHelper.JournalReviewExtra}")
                ).toJsonElements().toList()
                for (start in input.indices step 100) {
                    val inputBatch = input.subList(start, start + 100)
                    conn.batchInsert(inputBatch)
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