package processm.etl.metamodel

import processm.core.log.DBXESOutputStream
import processm.core.persistence.connection.DBCache
import processm.etl.discovery.SchemaCrawlerExplorer
import java.sql.DriverManager
import java.util.*
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Exemplary use cases, not real tests")
class MetaModelTest {
    val dataStoreDBName = "1c546119-96fb-47e9-a9e7-b57b07c1365e"
    val dataModelId = 3

    @Test
    fun `building meta model`() {
        val externalDataSourceConnectionString = ""
        DriverManager.getConnection(externalDataSourceConnectionString).use { connection ->
            val metaModel = MetaModel.build(
                dataStoreDBName,
                "meta-model",
                SchemaCrawlerExplorer(connection, "public")
            )
        }
    }

    @Test
    fun `extracting business perspectives from meta model`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer(dataStoreDBName, metaModelReader)
        val businessPerspectives = businessPerspectiveExplorer.discoverBusinessPerspectives(true)
    }

    @Test
    fun `extracting event logs from meta model and storing it as xes`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(metaModelReader)
        val metaModel = MetaModel(dataStoreDBName, metaModelReader, metaModelAppender)
        val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer(dataStoreDBName, metaModelReader)
        val businessPerspective = businessPerspectiveExplorer.discoverBusinessPerspectives(true, 0.1).first()
        val traceSet = metaModel.buildTracesForBusinessPerspective(businessPerspective.first)
        DBXESOutputStream(DBCache.get(dataStoreDBName).getConnection()).use { dbStream ->
            dbStream.write(MetaModelXESInputStream(businessPerspective.first.caseNotionClasses, traceSet , dataStoreDBName, dataModelId))
        }
    }
}
