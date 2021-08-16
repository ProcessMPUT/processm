package processm.etl.flink.continuous

import org.apache.flink.table.api.EnvironmentSettings
import org.apache.flink.table.api.TableEnvironment
import org.apache.flink.table.catalog.AbstractCatalog
import org.apache.flink.types.Row
import org.apache.flink.util.CloseableIterator
import java.io.File

/**
 * The environment to execute continuous SQL queries. A continuous query reads the records stored in the database and
 * then blocks until new record is available. When new record appears the query continues to yield it. Once read,
 * the record becomes unavailable to future queries.
 * This environment stores its state durably in a file. Each time an environment is created with the same
 * [stateDirectory], it restores the state if any. It allows to begin the next query from the state at which finished
 * reading the results of the previous query.
 *
 * @property hostname The database hostname.
 * @property port The database port.
 * @property user The database username.
 * @property password The database password.
 * @property catalog The database schema reader.
 * @property stateDirectory The directory to save the execution state in.
 */
abstract class ContinuousQueryEnvironment(
    val hostname: String,
    val port: Int,
    val user: String,
    val password: String,
    val catalog: AbstractCatalog,
    val stateDirectory: File = getDefaultStateDirectory(hostname, port, user)
) {
    companion object {
        private const val STATE_DIR_PARAM = "processm.etl.statedir"

        @JvmStatic
        protected fun getDefaultStateDirectory(hostname: String, port: Int, user: String): File {
            // TODO: change to getPropertyIgnoreCase() when merged with master
            val baseStateDir = System.getProperty(STATE_DIR_PARAM)!!
            return File(baseStateDir, "${hostname}_${port}_$user")
        }
    }

    init {
        stateDirectory.mkdirs()
    }

    private val flinkSavepoint = File(stateDirectory, "flink").absolutePath

    private val flink: TableEnvironment = setUpFlinkEnvironment()

    private fun setUpFlinkEnvironment(): TableEnvironment {
        val settings = EnvironmentSettings.newInstance().inStreamingMode().build()
        val tableEnv = TableEnvironment.create(settings)

        // Map the schema of the source database schema into Flink schema.
        tableEnv.registerCatalog(catalog.name, catalog)
        tableEnv.useCatalog(catalog.name) // for session

        return tableEnv
    }

    /**
     * Executes a continuous query against this environment. The method returns an unbounded blocking iterator on
     * the query results. A call to this iterator [CloseableIterator.next] method either returns the next [Row]
     * immediately or blocks until the next row is available. This iterator must be closed using the
     * [CloseableIterator.close] method.
     * Only a single query iterator involving table A may be open at a time. An attempt to open another
     * iterator using table A throws [IllegalStateException]. However, it is supported to open parallel iterators
     * involving different tables.
     *
     * @param statement A Flink SQL statement.
     */
    fun query(statement: String): CloseableIterator<Row> =
        flink.executeSql(statement).collect()

}
