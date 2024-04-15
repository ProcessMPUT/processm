package processm.core.models.bpmn

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayInputStream
import java.util.*

internal object BPMNTable : UUIDTable("bpmn") {
    val xml = text("xml")
}

/**
 * BPMN models are stored as XMLs in the database, since we don't really do anything with them at the backend, the
 * structure is quite complex and thus making a suitable DB representation would be a lot of work, and there exists a
 * standard XML representation.
 */
object DBSerializer {

    /**
     * Returns the model identified by [modelId] deserialized from XML
     */
    fun fetch(database: Database, modelId: UUID): BPMNModel =
        BPMNModel.fromXML(ByteArrayInputStream(fetchXML(database, modelId).toByteArray()))

    /**
     * Returns the XML stored in the DB directly, without deserializing it
     */
    fun fetchXML(database: Database, modelId: UUID): String = transaction(database) {
        BPMNTable.select { BPMNTable.id eq modelId }.limit(1).first().let { it[BPMNTable.xml] }
    }

    fun insert(database: Database, bpmn: BPMNModel): UUID = transaction(database) {
        BPMNTable.insertAndGetId { it[xml] = bpmn.toXML() }.value
    }

    private fun verify(bpmnXML: String) =
        require(BPMNXMLService.validate(ByteArrayInputStream(bpmnXML.toByteArray()))) { "The XML does not conform to the BPMN 2.0 XSD" }

    /**
     * Inserts an XML representation of a BPMN model without the need to deserialize it first.
     *
     * @throws IllegalArgumentException The XML does not conform to the BPMN 2.0 XSD
     */
    fun insert(database: Database, bpmnXML: String): UUID = transaction(database) {
        verify(bpmnXML)
        BPMNTable.insertAndGetId { it[xml] = bpmnXML }.value
    }

    fun update(database: Database, modelId: UUID, bpmn: BPMNModel): Unit = transaction(database) {
        BPMNTable.update(where = { BPMNTable.id eq modelId }) {
            it[BPMNTable.xml] = bpmn.toXML()
        }
    }

    /**
     * Updates an XML representation of the BPMN model referenced by [modelId] without the need to deserialize the XML first.
     *
     * @throws IllegalArgumentException The XML does not conform to the BPMN 2.0 XSD
     */
    fun update(database: Database, modelId: UUID, bpmnXML: String): Unit = transaction(database) {
        verify(bpmnXML)
        BPMNTable.update(where = { BPMNTable.id eq modelId }) {
            it[BPMNTable.xml] = bpmnXML
        }
    }

    fun delete(database: Database, modelId: UUID) = transaction(database) {
        BPMNTable.deleteWhere { BPMNTable.id eq modelId }
    }
}