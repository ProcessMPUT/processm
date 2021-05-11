package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.junit.jupiter.api.Test
import processm.core.log.*
import processm.core.log.Event
import processm.etl.discovery.SchemaCrawlerExplorer
import java.io.FileWriter
import java.io.StringWriter
import java.time.Instant
import javax.xml.stream.XMLOutputFactory
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.ExperimentalTime

class MetaModelTest {

//    val connectionString = "jdbc:postgresql://localhost:5432/dsroka?user=dsroka&password=dsroka"
    // PostgreSQL DvdRental
    val connectionString = "jdbc:postgresql://postgresql.domek.ovh:5432/dvdrental?user=postgres&password=Y42ZqwGAg^ESr\$q6"
    // MySQL Employees
//    val connectionString = "jdbc:mysql://mysql.domek.ovh:3306/employees?user=debezium&password=3b5N4enx23cfNDNH"
    val targetDBName = "1c546119-96fb-47e9-a9e7-b57b07c1365e"
    val dataModelId = 3

    @Test
    fun `building meta model`() {
        val metaModel = MetaModel.build(targetDBName, "meta-model", SchemaCrawlerExplorer(connectionString, "public"))
    }

    @Test
    fun `building meta model from northwind`() {
        val connectionString = "jdbc:postgresql://postgresql.domek.ovh:5432/northwind?user=postgres&password=Y42ZqwGAg^ESr\$q6"
        val metaModel = MetaModel.build(targetDBName, "meta-model-northwind", SchemaCrawlerExplorer(connectionString, "public"))
    }

    @Test
    fun `building meta model from sakila`() {
        val connectionString = "jdbc:postgresql://postgresql.domek.ovh:5432/sakila?user=postgres&password=Y42ZqwGAg^ESr\$q6"
        val metaModel = MetaModel.build(targetDBName, "meta-model-sakila", SchemaCrawlerExplorer(connectionString, "public"))
    }

    @Test
    fun `building meta model from adventure works`() {
        val connectionString = "jdbc:sqlserver://sqlserver.domek.ovh:1433;databaseName=AdventureWorks2019;user=sa;password=J3Xz9LUqaOhb6o9z"
        val metaModel = MetaModel.build(targetDBName, "meta-model-adventure-works", SchemaCrawlerExplorer(connectionString, "AdventureWorks2019.*"))
    }

    @Test
    fun `building meta model from wide world importers`() {
        val connectionString = "jdbc:sqlserver://sqlserver.domek.ovh:1433;databaseName=WideWorldImporters;user=sa;password=J3Xz9LUqaOhb6o9z"
        val metaModel = MetaModel.build(targetDBName, "meta-model-wide-world-importers", SchemaCrawlerExplorer(connectionString, "WideWorldImporters.*"))
    }

//    val classIdsMap = mapOf(
//        "rental" to EntityID(13, Classes),
//        "payment" to EntityID(15, Classes),
//        "customer" to EntityID(11, Classes),
//        "inventory" to EntityID(10, Classes),
//        "city" to EntityID(5, Classes),
//        "address" to EntityID(7, Classes),
//        "staff" to EntityID(12, Classes),
//    )
    val classIdsMap = mapOf(
        "rental" to EntityID(43, Classes),
        "payment" to EntityID(45, Classes),
        "customer" to EntityID(41, Classes),
        "inventory" to EntityID(40, Classes),
        "city" to EntityID(35, Classes),
        "address" to EntityID(37, Classes),
        "staff" to EntityID(42, Classes),
    )

    @Test
    fun `extracting case notions from meta model`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(dataModelId, metaModelReader)
        val caseNotionScorer = CaseNotionScorer(targetDBName, metaModelReader)
        val metaModel = MetaModel(targetDBName, dataModelId, metaModelReader, metaModelAppender)
        //        val caseNotions = metaModel.extractCaseNotions("customer", setOf("customer", "payment", "rental"))
        val caseNotionExplorer = DAGCaseNotionExplorer(targetDBName, metaModelReader).discoverCaseNotions(true)
//        val caseNotions = caseNotionExplorer.discoverCaseNotions(classIdsMap["rental"]!!, setOf("rental", "payment", "inventory", "city", "staff", "address").map { classIdsMap[it]!! }.toSet())
//        val scoredCaseNotions = caseNotionScorer.scoreCaseNotions(caseNotions)
//        val lastCaseNotions = scoredCaseNotions.toList().sortedByDescending { it.second }

//        val logsSets = lastCaseNotions.map { (caseNotion, value) ->
//            val traces1 = metaModel.buildTracesForCaseNotion(caseNotion)
//            metaModel.transformToEventsLogs(traces1)
//        }

        val a = 2
    }

    @ExperimentalTime
    @Test
    fun `extracting case notions from meta model3`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(dataModelId, metaModelReader)
        val caseNotionScorer = CaseNotionScorer(targetDBName, metaModelReader)
        val metaModel = MetaModel(targetDBName, dataModelId, metaModelReader, metaModelAppender)
        //        val caseNotions = metaModel.extractCaseNotions("customer", setOf("customer", "payment", "rental"))

//        val duration = measureTimeMillis {
            val caseNotions = DAGCaseNotionExplorer(targetDBName, metaModelReader).discoverCaseNotions(true, 0.1)
            println(caseNotions.size)
//        }

//        println(duration.toDuration(DurationUnit.MILLISECONDS).inSeconds)

//        val selectedCaseNotion = caseNotions[3].first
//        val selectedCaseNotion = caseNotions[3].first
//        caseNotions.forEach { caseNotion ->
            val selectedCaseNotion = caseNotions[0].first.modifyIdentifyingObjects { classesGraph ->
                classesGraph.vertexSet().filter { it.value != 45 }.toSet()
            }
//            var skip = false
//            if (!skip) {
                val traceSet = metaModel.buildTracesForCaseNotion(selectedCaseNotion)
                val traces = traceSet.map { metaModel.transformToEventsLogs(it) }

        FileWriter("/Users/danielek/example.xes").use { received ->
            val writer = XMLXESOutputStream(XMLOutputFactory.newInstance().createXMLStreamWriter(received)).use { writer ->
                val log = Log()
                writer.write(log)
                traces.forEach { trace ->
                    val traceEventStream = Trace()
                    writer.write(traceEventStream)
                    trace.forEach { (timestamp, event) ->
                        val streamEvent = Event(timestamp!!, "${event.changeType} ${event.classId}", "$${event.classId} ${event.objectId}")
                        writer.write(streamEvent)
                    }
                }
            }

//            val output = XMLXESInputStream(received.toString().byteInputStream()).iterator()

//            with(output.next() as Log) {
//                assertNull(xesFeatures)
//            }
//
//            with(output.next() as Event) {
//                assertEquals(attributes.size, 0)
//            }

//            assertFalse(output.hasNext())
        }

//            }
//        }
//        val traceSet = metaModel.buildTracesForCaseNotion(selectedCaseNotion)
//        val logs = traceSet.map { metaModel.transformToEventsLogs(it) }

        val a = 2
    }

//    @Test
//    fun `extracting case notions from meta model2`() {
//        val metaModelReader = MetaModelReader(dataModelId)
//        val metaModelAppender = MetaModelAppender(dataModelId, metaModelReader)
//        val caseNotionScorer = CaseNotionScorer(targetDBName, metaModelReader)
//        val metaModel = MetaModel(targetDBName, dataModelId, metaModelReader, metaModelAppender)
//        val caseNotionExplorer = CaseNotionExplorer(targetDBName, metaModelReader)
////        val caseNotions = caseNotionExplorer.discoverCaseNotions(classIdsMap["customer"]!!, setOf("rental", "payment", "customer").map { classIdsMap[it]!! }.toSet())
////        val lastCaseNotion = caseNotions.maxByOrNull { it.value }!!.key
//        val lastCaseNotion = TreeCaseNotionDefinition(mapOf(classIdsMap["customer"]!! to null, classIdsMap["rental"]!! to classIdsMap["customer"]!!, classIdsMap["payment"]!! to classIdsMap["rental"]!!), setOf("customer").map { classIdsMap[it]!! }.toSet())
////        val scores = caseNotionScorer.scoreCaseNotions()
//        val traces = metaModel.buildTracesForCaseNotion(lastCaseNotion)
//        val logsSets = metaModel.transformToEventsLogs(traces)
//        val a = 2
//    }
}