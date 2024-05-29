package processm.etl.helpers

import processm.core.communication.Producer
import processm.core.persistence.connection.transactionMain
import processm.dbmodels.models.*
import java.util.*


/**
 * Sends [WorkspaceComponentEventType.NewExternalData] to all components in the data store identified by [dataStoreId].
 * The components are responsible for deciding whether the change is relevant to them.
 */
fun notifyAboutNewData(dataStoreId: UUID) {
    val producer = Producer()
    transactionMain {
        WorkspaceComponent.find { WorkspaceComponents.dataStoreId eq dataStoreId }
            .forEach { it.triggerEvent(producer, WorkspaceComponentEventType.NewExternalData) }
    }
}