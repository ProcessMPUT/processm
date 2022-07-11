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

    override fun getClasses(): Set<Class> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        return catalog.schemas
            .flatMap { catalog.getTables(it) }
            .filter { it !is View }
            .mapToSet { it.convertToClass() }
    }

    override fun getRelationships(): Set<Relationship> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        return catalog.schemas
            .flatMap { catalog.getTables(it) }
            .filter { it !is View }
            .flatMapTo(HashSet()) {
                it.importedForeignKeys.map {
                    val sourceColumn = it.columnReferences.first().foreignKeyColumn
                    val targetColumn = it.columnReferences.first().primaryKeyColumn
                    Relationship(it.name, sourceColumn.parent.convertToClass(), targetColumn.parent.convertToClass(), sourceColumn.name)
                }
            }
    }

    override fun close() {
        connection.close()
    }

    private fun Table.convertToClass() = Class(this.name, this.columns.map {Attribute(it.name, it.columnDataType.name, it.isPartOfForeignKey) })
}
