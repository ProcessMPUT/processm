package processm.etl.discovery

import java.sql.DriverManager
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore("Exemplary use cases, not real tests")
class SchemaCrawlerExplorerTest {

    val externalDataSourceConnectionString = ""

    @Test
    fun `getting schema info`() {
        DriverManager.getConnection(externalDataSourceConnectionString).use { connection ->
            val explorer = SchemaCrawlerExplorer(connection, "public")
            val classes = explorer.getClasses()
            val relationships = explorer.getRelationships()
        }
    }
}
