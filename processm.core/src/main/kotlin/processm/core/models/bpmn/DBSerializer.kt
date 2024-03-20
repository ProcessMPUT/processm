package processm.core.models.bpmn

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
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
    fun fetch(database: Database, modelId: UUID): String = transaction(database) {
        BPMNTable.select { BPMNTable.id eq modelId }.limit(1).first().let { it[BPMNTable.xml] }
    }

    fun insert(database: Database, bpmnXML: String): UUID = transaction(database) {
        BPMNTable.insertAndGetId { it[xml] = bpmnXML }.value
    }

    fun update(database: Database, modelId: UUID, bpmnXML: String): Unit = transaction(database) {
        BPMNTable.update(where = { BPMNTable.id eq modelId }) {
            it[BPMNTable.xml] = bpmnXML
        }
    }

    fun delete(database: Database, modelId: UUID) = transaction(database) {
        BPMNTable.deleteWhere { BPMNTable.id eq modelId }
    }
}