package processm.services.logic

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.joda.time.DateTime
import processm.core.persistence.connection.DBCache
import processm.dbmodels.models.DataSource
import processm.dbmodels.models.DataSourceDto
import processm.dbmodels.models.DataSources
import processm.dbmodels.models.Organizations
import java.util.*

class DataSourceService {
    /**
     * Returns all data sources for the specified [organizationId].
     */
    fun allByOrganizationId(organizationId: UUID): List<DataSourceDto> {
        return transaction(DBCache.getMainDBPool().database) {
            val query = DataSources.select { DataSources.organizationId eq organizationId }
            return@transaction DataSource.wrapRows(query).map { it.toDto() }
        }
    }

    /**
     * Create new data source named [name] and assigned to the specified [organizationId].
     */
    fun createDataSource(organizationId: UUID, name: String): DataSource {
        return transaction(DBCache.getMainDBPool().database) {
            val dataSourceId = DataSources.insertAndGetId {
                it[this.name] = name
                it[this.creationDate] = DateTime.now()
                it[this.organizationId] = EntityID(organizationId, Organizations)
            }

            return@transaction getById(dataSourceId.value)
        }
    }

    /**
     * Returns data source struct by it identifier.
     */
    private fun getById(dataSourceId: UUID) = transaction(DBCache.getMainDBPool().database) {
        return@transaction DataSource[dataSourceId]
    }
}