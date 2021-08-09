package processm.etl.discovery

import org.junit.jupiter.api.Test


class SchemaCrawlerExplorerTest {

    val externalDataSourceConnectionString = ""

    @Test
    fun `getting schema info`() {
        val explorer = SchemaCrawlerExplorer(externalDataSourceConnectionString, "public")
        val classes = explorer.getClasses()
        val relationships = explorer.getRelationships()
    }
}