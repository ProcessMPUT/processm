package processm.etl.metamodel

import io.mockk.InternalPlatformDsl.toStr
import io.mockk.mockk
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.graph.DefaultDirectedGraph
import org.junit.jupiter.api.*
import processm.core.DBTestHelper
import processm.core.logging.logger
import processm.core.persistence.connection.DBCache
import processm.etl.DBMSEnvironment
import processm.etl.PostgreSQLEnvironment
import processm.etl.discovery.SchemaCrawlerExplorer
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("SqlResolve")
@Tag("ETL")
@Timeout(120, unit = TimeUnit.SECONDS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DAGBusinessPerspectiveExplorerIntegrationTest {

    // region environment
    private val logger = logger()
    private val dataStoreName = DBTestHelper.dbName
    private lateinit var externalDB: DBMSEnvironment<*>
    // endregion

    protected fun initExternalDB(): DBMSEnvironment<*> = PostgreSQLEnvironment.getSakila()

    // region lifecycle management
    @BeforeAll
    fun setUp() {
        externalDB = initExternalDB()
    }

    @AfterAll
    fun tearDown() {
        externalDB.close()
    }
    // endregion

    @Test
    fun test() {

        val caseNotions = transaction(DBCache.get(dataStoreName).database) {
            val dataModelId =
                MetaModel.build(dataStoreName, metaModelName = "", SchemaCrawlerExplorer(externalDB.connect()))
            val metaModelReader = MetaModelReader(dataModelId.value)
            val businessPerspectiveExplorer = DAGBusinessPerspectiveExplorer(dataStoreName, metaModelReader)
            val classNames = metaModelReader.getClassNames()
            return@transaction businessPerspectiveExplorer.discoverBusinessPerspectives(true)
                .sortedBy { (_, score) -> score }
                .map { (businessPerspective, _) ->
                    val relations = businessPerspective.caseNotionClasses
                        .flatMap { classId ->
                            businessPerspective.getSuccessors(classId).map { classId.value to it.value }
                        }
                    businessPerspective.caseNotionClasses.map { classId -> "${classId.value}" to classNames[classId]!! } to relations
                }
        }
    }

}