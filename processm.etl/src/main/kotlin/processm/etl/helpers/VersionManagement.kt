package processm.etl.helpers

import processm.core.communication.Producer
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.CREATE_OR_UPDATE
import processm.dbmodels.models.WorkspaceComponent
import processm.dbmodels.models.WorkspaceComponents
import processm.dbmodels.models.triggerEvent
import java.sql.Connection
import java.util.*

/**
 * Returns the next version number from the DB sequence `xes_version`
 */
fun Connection.nextVersion(): Long = prepareStatement("select nextval('xes_version');").use {
    it.executeQuery().use { rs ->
        check(rs.next())
        rs.getLong(1)
    }
}

/**
 * Sends [CREATE_OR_UPDATE] to all components in the data store identified by [dataStoreId].
 * The components are responsible for deciding whether the change is relevant to them.
 */
fun notifyAboutNewData(dataStoreId: UUID) {
    val producer = Producer()
    transactionMain {
        WorkspaceComponent.find { WorkspaceComponents.dataStoreId eq dataStoreId }
            .forEach { it.triggerEvent(producer, CREATE_OR_UPDATE) }
    }
}