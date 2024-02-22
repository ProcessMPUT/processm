package processm.etl.discovery

import processm.core.helpers.mapToSet
import schemacrawler.inclusionrule.RegularExpressionInclusionRule
import schemacrawler.schema.Table
import schemacrawler.schema.View
import schemacrawler.schemacrawler.*
import schemacrawler.tools.utility.SchemaCrawlerUtility
import java.sql.Connection

class SchemaCrawlerExplorer(private val connection: Connection, schema: String? = null) : DatabaseExplorer {

    private val options: SchemaCrawlerOptions

    init {
        val limitOptionsBuilder: LimitOptionsBuilder = LimitOptionsBuilder.builder()
            .includeSchemas(RegularExpressionInclusionRule(schema))
        val loadOptionsBuilder: LoadOptionsBuilder = LoadOptionsBuilder.builder()
            .withSchemaInfoLevel(SchemaInfoLevelBuilder.standard())

        options = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
            .withLimitOptions(limitOptionsBuilder.toOptions())
            .withLoadOptions(loadOptionsBuilder.toOptions())
    }

    private val tables: List<Table> by lazy {
        // The performance of SchemaCralwerUtility varies wildly from DBMS to DBMS even on seemingly identical DB schema.
        // For example, with Sakila, on Postgres it takes negligible amount of time, whereas on MSSQL it takes around 10 seconds
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)
        catalog.schemas
            .flatMap { catalog.getTables(it) }
            .filter { it !is View }
    }

    override fun getClasses(): Set<Class> {
        return tables.mapToSet { it.convertToClass() }
    }

    override fun getRelationships(): Set<Relationship> {
        return tables
            .flatMapTo(HashSet()) {
                it.importedForeignKeys.map {
                    val sourceColumn = it.columnReferences.first().foreignKeyColumn
                    val targetColumn = it.columnReferences.first().primaryKeyColumn
                    Relationship(
                        it.name,
                        sourceColumn.parent.convertToClass(),
                        targetColumn.parent.convertToClass(),
                        sourceColumn.name
                    )
                }
            }
    }

    override fun close() {
        connection.close()
    }

    private fun Table.convertToClass() =
        Class(this.name, this.columns.map { Attribute(it.name, it.columnDataType.name, it.isPartOfForeignKey) })
}
