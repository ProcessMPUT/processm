package processm.experimental.etl.flink.continuous

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.helpers.loadConfiguration
import processm.logging.logger
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.concurrent.thread
import kotlin.test.*

@Suppress("SqlResolve")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Ignore("Cause random errors; probably race condition or other state-related issue")
class PostgreSQLContinuousEnvironmentTests {
    val logger = logger()

    // #region test data
    val sakilaSchema = "../test-databases/sakila/postgres-sakila-db/postgres-sakila-schema.sql"
    val sakilaData = "../test-databases/sakila/postgres-sakila-db/postgres-sakila-insert-data.sql"
    // #endregion

    // #region user input
    val dbName = "online_store"
    val user = "postgres"
    val password = "strong_password"

    /**
     * The parameters for connecting the source database.
     * This field is set by [setUpPostgreSQL] method.
     */
    val jdbcConnectionString by lazy { postgresContainer.jdbcUrl + "&user=$user&password=$password" }

    /**
     * The table containing source data. The entire tables will be read and monitored.
     */
    val sourceTables = listOf("rental", "payment")

    /**
     * The SQL query for transforming the data in the [sourceTables] into events. One event per row.
     */
    val getEventSQL = """
        SELECT 
            'rent' AS `concept:name`,
            'start' AS `lifecycle:transition`,
            rental_id AS `concept:instance`,
            rental_date AS `time:timestamp`
        FROM rental
        WHERE rental_date IS NOT NULL
    UNION
        SELECT 
            'rent' AS `concept:name`,
            'complete' AS `lifecycle:transition`,
            rental_id AS `concept:instance`,
            return_date AS `time:timestamp`
        FROM rental
        WHERE return_date IS NOT NULL
    UNION
        SELECT
            'pay' AS `concept:name`,
            'complete' AS `lifecycle:transition`,
            payment_id AS `concept:instance`,
            payment_date AS `time:timestamp`
        FROM payment
    """.trimIndent()
    // #endregion

    private lateinit var postgresContainer: PostgreSQLContainer<*>

    @BeforeAll
    fun setUp() {
        loadConfiguration()
        System.setProperty("processm.etl.statedir", "./etl.state")
        postgresContainer = setUpPostgreSQL()
        initDB()
    }

    @AfterAll
    fun tearDown() {
        postgresContainer.close()
    }

    private fun getJDBCConnection(): Connection = DriverManager.getConnection(jdbcConnectionString)

    private fun setUpPostgreSQL(): PostgreSQLContainer<*> {
        val imageName = DockerImageName.parse("debezium/postgres:12")
            .asCompatibleSubstituteFor("postgres")
        val postgresContainer = PostgreSQLContainer(imageName)
            .withDatabaseName(dbName)
            .withUsername(user)
            .withEnv("POSTGRES_PASSWORD", password)
            .withPassword(password)
        Startables.deepStart(listOf(postgresContainer)).join()
        return postgresContainer
    }

    private fun initDB() {
        logger.info("Loading test database with the initial data")
        getJDBCConnection().use { connection ->
            connection.autoCommit = false

            connection.createStatement().use { s ->
                s.execute(File(sakilaSchema).readText())

                // These tables inherit from the payment table. The inheriting tables do not inherit primary key
                // constraints [1] and lose their default replica identities [2]. When replication is enabled,
                // PostgreSQL prevents updates and deletes from tables without replica identify. By setting REPLICA
                // IDENTITY FULL the entire row identifies itself [2].
                // [1] https://www.postgresql.org/docs/current/ddl-inherit.html
                // [2] https://www.postgresql.org/docs/current/sql-altertable.html
                s.execute("ALTER TABLE payment_p2007_01 REPLICA IDENTITY FULL")
                s.execute("ALTER TABLE payment_p2007_02 REPLICA IDENTITY FULL")
                s.execute("ALTER TABLE payment_p2007_03 REPLICA IDENTITY FULL")
                s.execute("ALTER TABLE payment_p2007_04 REPLICA IDENTITY FULL")
                s.execute("ALTER TABLE payment_p2007_05 REPLICA IDENTITY FULL")
                s.execute("ALTER TABLE payment_p2007_06 REPLICA IDENTITY FULL")

                s.execute(File(sakilaData).readText())
                connection.commit()
            }
        }
        logger.info("Test database loaded with the initial data")
    }

    @Test
    fun `read batch data by parts using the same environment`() {
        val environment = with(postgresContainer) {
            PostgreSQLContinuousEnvironment(
                containerIpAddress,
                firstMappedPort,
                dbName,
                user,
                password
            )
        }

        try {

            // we should see the total of 47954 events
            val expectedTotal = 47954
            var total = 0

            // read the first 10000 events
            val time = ArrayList<Long>()
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 0 until 10000) {
                    assertTrue(it.hasNext())
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    total++
                }
            }

            // read the next batch of events
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 10000 until expectedTotal) {
                    assertTrue(it.hasNext())
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    total++
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            time.add(System.currentTimeMillis())
            assertEquals(expectedTotal, total)

            for (i in 1 until time.size)
                println("Batch $i read in ${time[i] - time[i - 1]}ms.")

        } finally {
            environment.stateDirectory.deleteRecursively()
        }
    }

    @Test
    fun `read batch data by parts using different environments`() {
        val environment1 = with(postgresContainer) {
            PostgreSQLContinuousEnvironment(
                containerIpAddress,
                firstMappedPort,
                dbName,
                user,
                password
            )
        }

        val stateDir = environment1.stateDirectory

        try {
            // we should see the total of 47954 events
            val expectedTotal = 47954
            var total = 0

            // read the first 10000 events
            val time = ArrayList<Long>()
            time.add(System.currentTimeMillis())
            environment1.query(getEventSQL).use {
                for (i in 0 until 10000) {
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    total++
                }
            }

            val environment2 = with(postgresContainer) {
                PostgreSQLContinuousEnvironment(
                    containerIpAddress,
                    firstMappedPort,
                    dbName,
                    user,
                    password
                )
            }

            // read the next batch of events
            time.add(System.currentTimeMillis())
            environment2.query(getEventSQL).use {
                for (i in 10000 until expectedTotal) {
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment2.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            time.add(System.currentTimeMillis())
            assertEquals(expectedTotal, total)

            for (i in 1 until time.size)
                println("Batch $i read in ${time[i] - time[i - 1]}ms.")

        } finally {
            stateDir.deleteRecursively()
        }
    }

    @Test
    fun `read batch data then read data written when system was not reading`() {
        val environment = with(postgresContainer) {
            PostgreSQLContinuousEnvironment(
                containerIpAddress,
                firstMappedPort,
                dbName,
                user,
                password
            )
        }
        var rentalId: Int = -1
        var paymentId: Int = -1

        try {
            // we should see the total of 47954 + 2 events
            val expectedTotalInBatch = 47954
            val expectedTotalStreamed = 2
            var total = 0

            // read all events from the database
            val time = ArrayList<Long>()
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 0 until expectedTotalInBatch) {
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    total++
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            // insert new records
            getJDBCConnection().use {
                it.autoCommit = false
                it.createStatement().use { stmt ->
                    val rs = stmt.executeQuery(
                        "insert into rental (rental_date,inventory_id,customer_id,return_date,staff_id) " +
                                "values ('2021-08-13 10:00:00.000',367,130,'2021-08-16 18:04:30.000',1) " +
                                "returning rental_id"
                    )
                    assertTrue(rs.next())
                    rentalId = rs.getInt(1)


                    stmt.execute(
                        "insert into payment (customer_id,staff_id,rental_id,amount,payment_date) " +
                                "values (130,1,$rentalId,'92.99','2021-08-13 10:01:37.567') "
                    )
                    val rs2 = stmt.executeQuery("SELECT max(payment_id) FROM payment")
                    assertTrue(rs2.next())
                    paymentId = rs2.getInt(1)
                }
                it.commit()
            }


            // read the inserted events
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 0 until expectedTotalStreamed) {
                    val record = it.next()
                    val conceptName = record.getField(0).toString()
                    val conceptInstance = record.getFieldAs<Int>(2)
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    assertTrue(conceptName == "rent" && conceptInstance == rentalId || conceptName == "pay" && conceptInstance == paymentId)
                    total++
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            time.add(System.currentTimeMillis())
            assertEquals(expectedTotalInBatch + expectedTotalStreamed, total)

            for (i in 1 until time.size)
                println("Batch $i read in ${time[i] - time[i - 1]}ms.")

        } finally {
            environment.stateDirectory.deleteRecursively()
            getJDBCConnection().use {
                it.autoCommit = false
                it.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM payment WHERE payment_id=$paymentId")
                    stmt.execute("DELETE FROM rental WHERE rental_id=$rentalId")
                }
                it.commit()
            }
        }
    }

    @Test
    fun `read batch data then wait until new data`() {
        val environment = with(postgresContainer) {
            PostgreSQLContinuousEnvironment(
                containerIpAddress,
                firstMappedPort,
                dbName,
                user,
                password
            )
        }
        var rentalId: Int = -1
        var paymentId: Int = -1

        try {
            // we should see the total of 47954 + 2 events
            val expectedTotalInBatch = 47954
            val expectedTotalStreamed = 2
            var total = 0

            // read all events from the database
            val time = ArrayList<Long>()
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 0 until expectedTotalInBatch) {
                    val conceptName = it.next().getField(0).toString()
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    total++
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            // insert new records
            thread {
                Thread.sleep(2000L)
                println("Inserting new records")
                getJDBCConnection().use {
                    it.autoCommit = false
                    it.createStatement().use { stmt ->
                        val rs = stmt.executeQuery(
                            "insert into rental (rental_date,inventory_id,customer_id,return_date,staff_id) " +
                                    "values ('2021-08-13 10:00:00.000',367,130,'2021-08-16 18:04:30.000',1) " +
                                    "returning rental_id"
                        )
                        assertTrue(rs.next())
                        rentalId = rs.getInt(1)

                        stmt.execute(
                            "insert into payment (customer_id,staff_id,rental_id,amount,payment_date) " +
                                    "values (130,1,$rentalId,'92.99','2021-08-13 10:01:37.567') "
                        )
                        val rs2 = stmt.executeQuery("SELECT max(payment_id) FROM payment")
                        assertTrue(rs2.next())
                        paymentId = rs2.getInt(1)
                    }
                    it.commit()
                }
            }

            // read the inserted events - should block until the thread above writes data
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use {
                for (i in 0 until expectedTotalStreamed) {
                    val record = it.next()
                    val conceptName = record.getField(0).toString()
                    val conceptInstance = record.getFieldAs<Int>(2)
                    assertTrue(conceptName == "rent" || conceptName == "pay")
                    assertTrue(conceptName == "rent" && conceptInstance == rentalId || conceptName == "pay" && conceptInstance == paymentId)
                    total++
                }
            }

            // try to read the next event - should block
            time.add(System.currentTimeMillis())
            environment.query(getEventSQL).use { iter ->
                thread {
                    Thread.sleep(5000L)
                    iter.close()
                }
                assertFailsWith<RuntimeException> {
                    iter.next()
                    total++
                }
            }

            time.add(System.currentTimeMillis())
            assertEquals(expectedTotalInBatch + expectedTotalStreamed, total)

            for (i in 1 until time.size)
                println("Batch $i read in ${time[i] - time[i - 1]}ms.")

        } finally {
            environment.stateDirectory.deleteRecursively()
            getJDBCConnection().use {
                it.autoCommit = false
                it.createStatement().use { stmt ->
                    stmt.execute("DELETE FROM payment WHERE payment_id=$paymentId")
                    stmt.execute("DELETE FROM rental WHERE rental_id=$rentalId")
                }
                it.commit()
            }
        }
    }
}
