package processm.etl.jdbc

import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import processm.core.log.*
import processm.core.log.attribute.value
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.core.querylanguage.Query
import processm.dbmodels.etl.jdbc.ETLColumnToAttributeMap
import processm.dbmodels.etl.jdbc.ETLConfiguration
import processm.dbmodels.etl.jdbc.ETLConfigurations
import processm.etl.DBMSEnvironment
import processm.etl.MSSQLEnvironment
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.util.*
import kotlin.test.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MSSQLWorldWideImportersTest {

    // region environment
    private val logger = logger()
    private lateinit var dataStoreName: String
    private lateinit var externalDB: DBMSEnvironment<*>
    // endregion

    // region user input
    private val etlConfiguratioName = "ETL Configuration for WorldWideImporters"

    private fun getEventQuery(batch: Boolean) = """
WITH MainOrderAux(ParentID, ChildID) AS 
(
	SELECT o1.OrderID as ParentID, o1.BackorderOrderID as ChildID
	FROM WideWorldImporters.Sales.Orders o1
	UNION ALL
	SELECT o1.ParentID as ParentID, o2.BackorderOrderID as ChildID
	FROM MainOrderAux o1 JOIN WideWorldImporters.Sales.Orders o2 on o1.ChildID = o2.OrderID 
),
MainOrder(ParentID, ChildID) AS
(
	SELECT o.OrderID as ParentID, o.OrderID as ChildID
	FROM WideWorldImporters.Sales.Orders o
	WHERE o.OrderID NOT IN (SELECT BackorderOrderID FROM WideWorldImporters.Sales.Orders WHERE BackorderOrderID IS NOT NULL)
	UNION ALL
	SELECT *
	FROM MainOrderAux
	WHERE ChildID is not NULL AND ParentID NOT IN (SELECT BackorderOrderID FROM WideWorldImporters.Sales.Orders WHERE BackorderOrderID IS NOT NULL)
)
select * from (
select *, ROW_NUMBER() OVER (ORDER BY "trace_id", "time:timestamp", "concept:instance") AS "event_id" FROM (
select "concept:name", "concept:instance", "time:timestamp", (select ParentID from MainOrder mo where mo.ChildID=sub.OrderID) as "trace_id" FROM (
select 'placed' as "concept:name", o.OrderID as "concept:instance", o.OrderID, o.OrderDate as "time:timestamp" from WideWorldImporters.Sales.Orders o
union all
select 'item picked' as "concept:name", ol.OrderLineID as "concept:instance", ol.OrderID, ol.PickingCompletedWhen as "time:timestamp" from WideWorldImporters.Sales.OrderLines ol
union all
select 'picking complete' as "concept:name", o.OrderID as "concept:instance", o.OrderID, o.PickingCompletedWhen as "time:timestamp" from WideWorldImporters.Sales.Orders o
union all
select 'invoice issued' as "concept:name", i.InvoiceID as "concept:instance", i.OrderID, i.InvoiceDate as "time:timestamp" from WideWorldImporters.Sales.Invoices i
union all 
select 'delivered' as "concept:name", i.InvoiceID as "concept:instance", i.OrderID, i.ConfirmedDeliveryTime as "time:timestamp" from WideWorldImporters.Sales.Invoices i
) sub
) sub2
) sub3""".trimIndent() +
            (if (batch) "" else """ where "event_id" > coalesce(?, 0)""") + """ order by "event_id""""

    private fun createEtlConfiguration(lastEventExternalId: String? = "0") {
        dataStoreName = UUID.randomUUID().toString()
        transaction(DBCache.get(dataStoreName).database) {
            val config = ETLConfiguration.new {
                name = etlConfiguratioName
                jdbcUri = externalDB.jdbcUrl
                user = externalDB.user
                password = externalDB.password
                query = getEventQuery(lastEventExternalId == null)
                this.lastEventExternalId = lastEventExternalId
                batch = lastEventExternalId == null
            }

            ETLColumnToAttributeMap.new {
                configuration = config
                sourceColumn = "event_id"
                target = "event_id"
                eventId = true
            }

            ETLColumnToAttributeMap.new {
                configuration = config
                sourceColumn = "trace_id"
                target = "trace_id"
                traceId = true
            }
        }
    }
    // endregion


    @BeforeAll
    fun setUp() {
        externalDB = MSSQLEnvironment.getWWI()

    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }

    @BeforeTest
    fun setUpETL() {
        createEtlConfiguration()
    }

    @AfterTest
    fun resetState() {
        DBCache.get(dataStoreName).getConnection().use { conn ->
            conn.autoCommit = false

            conn.createStatement().use { stmt ->
                stmt.execute("UPDATE etl_configurations SET last_event_external_id=0")
                stmt.executeQuery("""SELECT l.id FROM logs l JOIN etl_configurations e ON l."identity:id"=e.log_identity_id""")
                    .use {
                        while (it.next())
                            DBLogCleaner.removeLog(conn, it.getInt(1))
                    }
            }
            conn.commit()
        }
        DBCache.getMainDBPool().getConnection().use { conn ->
            conn.prepareStatement("""DROP DATABASE "$dataStoreName"""")
        }
        transaction(DBCache.get(dataStoreName).database) {
            ETLConfigurations.deleteAll()
        }
    }


    @Test
    fun `read XES from existing data`() {

        logger.info("Reading XES...")
        val stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().toList()
        }

        assertEquals(1, stream.filterIsInstance<Log>().size)
        assertEquals(66057, stream.filterIsInstance<Trace>().size)
        assertEquals(519622, stream.filterIsInstance<Event>().size)

        logger.info("Verifying contents...")
        assertTrue(stream
            .filterIsInstance<Event>()
            .all {
                it.conceptName in setOf(
                    "placed",
                    "item picked",
                    "picking complete",
                    "invoice issued",
                    "delivered"
                )
            }
        )
    }

    @Test
    fun `read the first two events one at a time`() {
        val partSize = 3
        var logUUID: UUID? = null
        logger.info("Importing the first event")
        val part1 = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            val materialized = etl.toXESInputStream().take(partSize).toList()

            assertEquals("1", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
            return@transaction materialized
        }

        assertEquals(partSize, part1.size)
        for ((i, x) in part1.withIndex())
            logger.debug("$i ${x::class.simpleName} ${x.identityId}")
        assertEquals(1, part1.filterIsInstance<Log>().size)
        assertEquals(logUUID, part1.filterIsInstance<Log>().single().identityId)
        assertEquals(1, part1.filterIsInstance<Trace>().size)
        assertEquals(1, part1.filterIsInstance<Event>().size)
        val event1 = part1.filterIsInstance<Event>().single()
        assertEquals(UUID.fromString("00000000-0000-0000-0000-000000000001"), event1.identityId)

        logger.info("Importing one more event")
        val part2 = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            val part2 = etl.toXESInputStream().take(3).toList()

            for ((i, x) in part2.withIndex())
                logger.debug("$i ${x::class.simpleName} ${x.identityId}")

            assertEquals("2", etl.lastEventExternalId)
            return@transaction part2
        }

        assertEquals(3, part2.size)
        assertEquals(1, part2.filterIsInstance<Log>().size)
        assertEquals(1, part2.filterIsInstance<Trace>().size)
        assertEquals(1, part2.filterIsInstance<Event>().size)
        assertEquals(
            logUUID,
            part2.filterIsInstance<Log>().single().identityId
        )
        assertEquals(
            part1.filterIsInstance<Trace>().single().identityId,
            part2.filterIsInstance<Trace>().single().identityId
        )
        val event2 = part2.filterIsInstance<Event>().single()
        assertNotEquals(event1.identityId, event2.identityId)
        assertEquals(
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            event2.identityId
        )
    }

    @Test
    fun `import the first two XES batches into the data store`() {
        /*
         * Values in this test can be precomputed with the following query:

WITH MainOrderAux(ParentID, ChildID) AS
(
	SELECT o1.OrderID as ParentID, o1.BackorderOrderID as ChildID
	FROM WideWorldImporters.Sales.Orders o1
	UNION ALL
	SELECT o1.ParentID as ParentID, o2.BackorderOrderID as ChildID
	FROM MainOrderAux o1 JOIN WideWorldImporters.Sales.Orders o2 on o1.ChildID = o2.OrderID
),
MainOrder(ParentID, ChildID) AS
(
	SELECT o.OrderID as ParentID, o.OrderID as ChildID
	FROM WideWorldImporters.Sales.Orders o
	WHERE o.OrderID NOT IN (SELECT BackorderOrderID FROM WideWorldImporters.Sales.Orders WHERE BackorderOrderID IS NOT NULL)
	UNION ALL
	SELECT *
	FROM MainOrderAux
	WHERE ChildID is not NULL AND ParentID NOT IN (SELECT BackorderOrderID FROM WideWorldImporters.Sales.Orders WHERE BackorderOrderID IS NOT NULL)
)
select * from (
select *, dense_rank() over (order by "trace_id") as "trace_rank" from (
select *, ROW_NUMBER() OVER (ORDER BY "trace_id", "time:timestamp", "concept:instance") AS "event_id" FROM (
select "concept:name", "concept:instance", "time:timestamp", (select ParentID from MainOrder mo where mo.ChildID=sub.OrderID) as "trace_id" FROM (
select 'placed' as "concept:name", o.OrderID as "concept:instance", o.OrderID, o.OrderDate as "time:timestamp" from WideWorldImporters.Sales.Orders o
union all
select 'item picked' as "concept:name", ol.OrderLineID as "concept:instance", ol.OrderID, ol.PickingCompletedWhen as "time:timestamp" from WideWorldImporters.Sales.OrderLines ol
union all
select 'picking complete' as "concept:name", o.OrderID as "concept:instance", o.OrderID, o.PickingCompletedWhen as "time:timestamp" from WideWorldImporters.Sales.Orders o
union all
select 'invoice issued' as "concept:name", i.InvoiceID as "concept:instance", i.OrderID, i.InvoiceDate as "time:timestamp" from WideWorldImporters.Sales.Invoices i
union all
select 'delivered' as "concept:name", i.InvoiceID as "concept:instance", i.OrderID, i.ConfirmedDeliveryTime as "time:timestamp" from WideWorldImporters.Sales.Invoices i
) sub
) sub2
) sub3
) sub4
where "event_id" + "trace_rank" + n + (n - 1) <= n*partSize -- replace n with the batch number (starting from 1; n stands for starting a log in the stream and n-1 starts for starting an already existing trace in the consecutive batches); replace partSize with partSize
order by "trace_rank" desc, "event_id" desc
*
         */
        val partSize = 10000
        var logUUID: UUID? = null
        logger.info("Importing the first $partSize XES components...")
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materialized = etl.toXESInputStream().take(partSize).toList()
                out.write(materialized.asSequence())
            }

            assertEquals("8900", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        var counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )

        var log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(1099L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(8900L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)

        logger.info("Importing the next $partSize XES components...")
        // import the remaining components
        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            AppendingDBXESOutputStream(DBCache.get(dataStoreName).getConnection()).use { out ->
                val materialized = etl.toXESInputStream().take(partSize).toList()
                out.write(materialized.asSequence())
            }

            assertEquals("17768", etl.lastEventExternalId)
            logUUID = etl.logIdentityId
        }

        logger.info("Querying...")
        counts = DBHierarchicalXESInputStream(
            dataStoreName,
            Query("select count(l:id), count(t:id), count(e:id) where l:id=$logUUID")
        )
        log = counts.first()
        assertEquals(1L, log.attributes["count(log:identity:id)"]?.value)
        assertEquals(2229L, log.traces.first().attributes["count(trace:identity:id)"]?.value)
        assertEquals(17768L, log.traces.first().events.first().attributes["count(event:identity:id)"]?.value)
    }

    @Test
    fun `read XES from existing data then add new data next read XES`() {
        logger.info("Importing XES...")
        val stream1 = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().toList()
        }

        assertEquals(1, stream1.filterIsInstance<Log>().size)
        assertEquals(66057, stream1.filterIsInstance<Trace>().size)
        assertEquals(519622, stream1.filterIsInstance<Event>().size)

        // simulate new order
        val orderID = externalDB.connect().use { conn ->
            conn.autoCommit = false
            val orderID = conn.prepareStatement(
                """
                insert into WideWorldImporters.Sales.Orders 
                (CustomerID, SalespersonPersonID, ContactPersonID, OrderDate, ExpectedDeliveryDate, IsUndersupplyBackordered, PickingCompletedWhen, LastEditedBy)
                OUTPUT INSERTED.OrderID
                values
                (?, ?, ?, ?, ?, ?, ?, ?)                
                """
            ).use { stmt ->
                listOf(
                    575, 13, 3110, Date.valueOf("2021-08-30"),
                    Date.valueOf("2022-02-29"), false, Timestamp.valueOf("2021-08-30 18:05:30.123"), 7
                )
                    .forEachIndexed { idx, obj -> stmt.setObject(idx + 1, obj) }
                return@use stmt.executeQuery().use { rs ->
                    check(rs.next())
                    return@use rs.getInt(1)
                }
            }
            conn.prepareStatement(
                """
                insert into WideWorldImporters.Sales.OrderLines 
                (OrderID, StockItemID, Description, PackageTypeID, Quantity, TaxRate, PickedQuantity, PickingCompletedWhen, LastEditedBy)
                values
                (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent()
            ).use { stmt ->
                listOf(
                    listOf(
                        orderID, 9, "Dzik - odyniec, dorosły", 9, 1, 5, 1,
                        Timestamp.valueOf("2021-08-30 09:40:00.000"), 3
                    ),
                    listOf(
                        orderID, 10, "Dzik - locha, dorosła", 9, 10, 5, 1,
                        Timestamp.valueOf("2021-08-30 11:20:00.000"), 3
                    ),
                    listOf(
                        orderID, 11, "Dzik - pasiak", 9, 666, 5, 1,
                        Timestamp.valueOf("2021-08-30 17:50:00.000"), 3
                    )
                ).forEach { objs ->
                    objs.forEachIndexed { idx, obj -> stmt.setObject(idx + 1, obj) }
                    stmt.execute()
                }
            }
            conn.commit()
            return@use orderID
        }

        try {
            val stream2 = transaction(DBCache.get(dataStoreName).database) {
                val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
                return@transaction etl.toXESInputStream().toList()
            }
            assertEquals(1, stream2.filterIsInstance<Log>().size)
            assertEquals(1, stream2.filterIsInstance<Trace>().size)
            val events = stream2.filterIsInstance<Event>()
            assertEquals(5, events.size)
            assertEquals(events[0].timeTimestamp, Instant.parse("2021-08-30T00:00:00.000Z"))
            assertEquals(events[0].conceptName, "placed")
            assertEquals(events[1].timeTimestamp, Instant.parse("2021-08-30T09:40:00.000Z"))
            assertEquals(events[1].conceptName, "item picked")
            assertEquals(events[2].timeTimestamp, Instant.parse("2021-08-30T11:20:00.000Z"))
            assertEquals(events[2].conceptName, "item picked")
            assertEquals(events[3].timeTimestamp, Instant.parse("2021-08-30T17:50:00.000Z"))
            assertEquals(events[3].conceptName, "item picked")
            assertEquals(events[4].timeTimestamp, Instant.parse("2021-08-30T18:05:30.123Z"))
            assertEquals(events[4].conceptName, "picking complete")
        } finally {
            externalDB.connect().use { conn ->
                conn.prepareStatement("delete from WideWorldImporters.Sales.OrderLines  where OrderID=?").use { stmt ->
                    stmt.setObject(1, orderID)
                    stmt.execute()
                }
                conn.prepareStatement("delete from WideWorldImporters.Sales.Orders  where OrderID=?").use { stmt ->
                    stmt.setObject(1, orderID)
                    stmt.execute()
                }
            }
        }
    }

    @Test
    fun `read complete XES and then read nothing starting from 0`() {
        val stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().toList()
        }

        assertEquals(1, stream.filterIsInstance<Log>().size)
        assertEquals(66057, stream.filterIsInstance<Trace>().size)
        assertEquals(519622, stream.filterIsInstance<Event>().size)

        transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            assertEquals(0, etl.toXESInputStream().count())
        }
    }

    @Test
    fun `read something then read everything then read nothing from null`() {
        createEtlConfiguration(null)

        var stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().take(100).toList()
        }
        assertFalse { stream.isEmpty() }

        stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().toList()
        }
        assertEquals(1, stream.filterIsInstance<Log>().size)
        assertEquals(66057, stream.filterIsInstance<Trace>().size)
        assertEquals(519622, stream.filterIsInstance<Event>().size)

        stream = transaction(DBCache.get(dataStoreName).database) {
            val etl = ETLConfiguration.find { ETLConfigurations.name eq etlConfiguratioName }.first()
            return@transaction etl.toXESInputStream().toList()
        }
        assertTrue { stream.isEmpty() }
    }
}