package processm.etl.discovery

import org.junit.jupiter.api.Test


class SchemaCrawlerExplorerTest {

    val connectionString = "jdbc:postgresql://localhost:5432/dsroka?user=dsroka&password=dsroka"

    @Test
    fun `getting schema info`() {
        val explorer = SchemaCrawlerExplorer(connectionString, "public")
        val classes = explorer.getClasses()
        val relationships = explorer.getRelationships()
    }
}