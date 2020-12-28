package processm.etl.discovery

import schemacrawler.inclusionrule.RegularExpressionInclusionRule
import schemacrawler.schema.Table
import schemacrawler.schema.TableRelationshipType
import schemacrawler.schema.View
import schemacrawler.schemacrawler.*
import schemacrawler.utility.SchemaCrawlerUtility
import java.sql.DriverManager

internal class SchemaCrawlerExplorer(private val connectionString: String, private val schema: String) : DatabaseExplorer {

    private val options: SchemaCrawlerOptions
    private val connection = DriverManager.getConnection(connectionString)

    init {
        val limitOptionsBuilder: LimitOptionsBuilder = LimitOptionsBuilder.builder()
            .includeSchemas(RegularExpressionInclusionRule(schema))
        val loadOptionsBuilder: LoadOptionsBuilder = LoadOptionsBuilder.builder()
            .withSchemaInfoLevel(SchemaInfoLevelBuilder.standard())
        options = SchemaCrawlerOptionsBuilder.builder()
            .withLimitOptions(limitOptionsBuilder.toOptions())
            .withLoadOptions(loadOptionsBuilder.toOptions())
            .toOptions()
    }

    override fun getClasses(): Set<Class> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        return catalog.schemas
            .map { catalog.getTables(it) }
            .flatten()
            .filter { it !is View }
            .map { it.convertToClass() }
            .toSet()
    }

    override fun getRelationships(): Set<Relationship> {
        val catalog = SchemaCrawlerUtility.getCatalog(connection, options)

        return catalog.schemas
            .map { catalog.getTables(it) }.flatten().filter { it !is View }.map {
                it.importedForeignKeys.map {
                    val sourceColumn = it.columnReferences.first().foreignKeyColumn
                    val targetColumn = it.columnReferences.first().primaryKeyColumn
                    Relationship(it.name, sourceColumn.parent.convertToClass(), targetColumn.parent.convertToClass(), sourceColumn.name)
                }
            }
            .flatten()
            .toSet()
    }

    override fun close() {
        connection.close()
    }

    private fun Table.convertToClass() = Class(this.name, this.columns.map {Attribute(it.name, it.columnDataType.name, it.isPartOfForeignKey) })
}