package processm.etl.metamodel

import org.junit.jupiter.api.Test
import processm.etl.discovery.SchemaCrawlerExplorer

class MetaModelTest {
    val dataStoreDBName = "1c546119-96fb-47e9-a9e7-b57b07c1365e"
    val dataModelId = 3

    @Test
    fun `building meta model`() {
        val externalDataSourceConnectionString = ""
        val metaModel = MetaModel.build(dataStoreDBName, "meta-model", SchemaCrawlerExplorer(externalDataSourceConnectionString, "public"))
    }

    @Test
    fun `extracting business perspectives from meta model`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer(dataStoreDBName, metaModelReader)
        val businessPerspectives = businessPerspectiveExplorer.discoverBusinessPerspectives(true)
    }

    @Test
    fun `extracting event logs from meta model`() {
        val metaModelReader = MetaModelReader(dataModelId)
        val metaModelAppender = MetaModelAppender(metaModelReader)
        val metaModel = MetaModel(dataStoreDBName, metaModelReader, metaModelAppender)
        val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer(dataStoreDBName, metaModelReader)
        val businessPerspective = businessPerspectiveExplorer.discoverBusinessPerspectives(true, 0.1).first()
        val traceSet = metaModel.buildTracesForBusinessPerspective(businessPerspective.first)
        val logs = traceSet.map { metaModel.transformToEventsLogs(it) }
    }
}