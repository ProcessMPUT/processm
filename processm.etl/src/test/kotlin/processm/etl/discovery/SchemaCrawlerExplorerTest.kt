package processm.etl.discovery

import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Exemplary use cases, not real tests")
class SchemaCrawlerExplorerTest {

    val externalDataSourceConnectionString = ""

    @Test
    fun `getting schema info`() {
        val explorer = SchemaCrawlerExplorer(externalDataSourceConnectionString, "public")
        val classes = explorer.getClasses()
        val relationships = explorer.getRelationships()
    }
}
