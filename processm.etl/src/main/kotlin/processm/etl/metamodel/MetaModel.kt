package processm.etl.metamodel

import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.insertAndGetId
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache
import processm.etl.discovery.DbExplorer
import processm.etl.discovery.JdbcDbExplorer

class MetaModel private constructor(builder: Builder, dbExplorer: DbExplorer) {

    init {
        val classes = dbExplorer.getClasses()
        val relationships = dbExplorer.getRelationships()

        transaction(DBCache.get(builder.targetDatabaseName).database) {
            val dataModelId = DataModels.insertAndGetId {
                it[name] = builder.metaModelName
            }

            val classIds = classes.map { objectClass ->
                val classId = Classes.insertAndGetId {
                    it[Classes.name] = objectClass.name
                    it[Classes.dataModelId] = dataModelId
                }

                AttributesNames.batchInsert(objectClass.attributes) {
                    this[AttributesNames.name] = it.name
                    this[AttributesNames.type] = it.type
                    this[AttributesNames.classId] = classId
                }

                return@map objectClass to classId
            }.toMap()

            Relationships.batchInsert(relationships.filter { classIds.containsKey(it.sourceClass) && classIds.containsKey(it.targetClass) }) {
                this[Relationships.name] = it.name
                this[Relationships.sourceClassId] = classIds[it.sourceClass]!!
                this[Relationships.targetClassId] = classIds[it.targetClass]!!
            }
        }
    }

    companion object {
        inline fun build(block: Builder.() -> Unit) = Builder().apply(block).build()
    }

    class Builder {
        private var _dataSourceConnectionString: String? = null
        private var _targetDatabaseName: String? = null
        private var _metaModelName: String? = null
        private var _schemaName: String? = null

        val dataSourceConnectionString get() = _dataSourceConnectionString!!
        val targetDatabaseName get() = _targetDatabaseName!!
        val metaModelName get() = _metaModelName ?: ""
        val schemaName get() = _schemaName ?: ""

        fun withDataSource(connectionString: String): Builder {
            _dataSourceConnectionString = connectionString
            return this
        }

        fun withTargetDatabaseName(databaseName: String): Builder {
            _targetDatabaseName = databaseName
            return this
        }

        fun withMetaModelName(metaModelName: String): Builder {
            _metaModelName = metaModelName
            return this
        }

        fun withDataSourceSchema(schemaName: String): Builder {
            _schemaName = schemaName
            return this
        }

        fun validate() {
            requireNotNull(_dataSourceConnectionString) { "${Builder::_dataSourceConnectionString.name} should be defined" }
            requireNotNull(_targetDatabaseName) { "${Builder::_targetDatabaseName.name} should be defined" }
        }

        fun build(): MetaModel {
            validate()
            createAppropriateDbExplorer().use {
                return MetaModel(this, it)
            }
        }

        private fun createAppropriateDbExplorer(): DbExplorer {
            // use JdbcDbExplorer as a default
            return JdbcDbExplorer(dataSourceConnectionString, schemaName)
        }
    }
}