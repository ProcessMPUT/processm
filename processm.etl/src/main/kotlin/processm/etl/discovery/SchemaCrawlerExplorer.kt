package processm.etl.discovery

import processm.helpers.mapToSet
import schemacrawler.inclusionrule.RegularExpressionExclusionRule
import schemacrawler.inclusionrule.RegularExpressionInclusionRule
import schemacrawler.schema.Schema
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
            .includeSchemas(RegularExpressionExclusionRule("_timescaledb_.*"))
        val loadOptionsBuilder: LoadOptionsBuilder = LoadOptionsBuilder.builder()
            .withSchemaInfoLevel(SchemaInfoLevelBuilder.standard())

        options = SchemaCrawlerOptionsBuilder.newSchemaCrawlerOptions()
            .withLimitOptions(limitOptionsBuilder.toOptions())
            .withLoadOptions(loadOptionsBuilder.toOptions())
    }

    override fun getClasses(): Set<Class> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        return catalog.schemas.flatMapTo(HashSet()) { schema ->
            catalog.getTables(schema)
                .filter { it !is View }
                .mapToSet { it.convertToClass(schema) }
        }
    }

    override fun getRelationships(): Set<Relationship> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        val result = HashSet<Relationship>()
        for (schema in catalog.schemas) {
            for (table in catalog.getTables(schema)) {
                if (table !is View) {
                    table.importedForeignKeys.mapTo(result) {
                        val sourceColumn = it.columnReferences.first().foreignKeyColumn
                        val targetColumn = it.columnReferences.first().primaryKeyColumn
                        Relationship(
                            it.name,
                            sourceColumn.parent.convertToClass(schema),
                            targetColumn.parent.convertToClass(schema),
                            sourceColumn.name
                        )
                    }
                }
            }
        }
        return result
    }

    override fun close() {
        connection.close()
    }

    private fun Table.convertToClass(schema: Schema) =
        Class(schema.name, this.name,
            this.columns.map { Attribute(it.name, it.columnDataType.name, it.isPartOfForeignKey) })
}
