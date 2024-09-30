package processm.selenium

import org.jgroups.util.UUID
import org.junit.jupiter.api.*
import org.openqa.selenium.By
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.helpers.isUUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue


/**
 * The tests in this class are, in fact, a single test case split into multiple functions for ease of debugging and maintenance.
 * The rely on JUnit executing them in the order given by the @Order annotations.
 * It makes no sense to execute a single test (other than the first), as it is bound to fail.
 * [TestCaseAsAClass] (via [SeleniumBase]) ensures that if any test fails, all the remaining tests are not executed.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AutomaticEtlTest : SeleniumBase() {

    // region auxiliary variables

    private lateinit var dbContainer: PostgreSQLContainer<*>

    val email = UUID.randomUUID().toString() + "@example.com"
    val password = "TestTest123"
    val organization = "Org${UUID.randomUUID()}"
    val dataStoreName = "TestDS"
    val connectorName = "SomeConnector"

    private lateinit var logIdentityId: String


    // endregion

    // region setup

    @BeforeAll
    fun setupInputDB() {
        dbContainer = PostgreSQLContainer(
            DockerImageName.parse("debezium/postgres:16-alpine")
                .asCompatibleSubstituteFor("postgres")
        )
            .withUsername("postgres")
            .withPassword("password")
        Startables.deepStart(listOf(dbContainer)).join()
        val dbName = "inputDB${UUID.randomUUID()}"

        dbContainer.createConnection("")
            .use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("create database \"$dbName\"")
                }
            }
        dbContainer.withDatabaseName(dbName)
        dbContainer.createConnection("")
            .use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("create table EBAN (id int primary key, text text)")
                    stmt.execute("create table EKET (id int primary key, eban int references EBAN(id), text text)")
                    stmt.execute("create table EKKO (id int primary key, eban int references EBAN(id), text text)")
                    stmt.execute("create table EKPO (id int primary key, ekko int references EKKO(id), text text)")
                }
            }
    }

    @AfterAll
    fun takeDownInputDB() {
        dbContainer.close()
    }

    // endregion

    // region tests

    @Order(10)
    @Test
    fun register() {
        register(email, password, organization)
    }

    @Order(20)
    @Test
    fun login() {
        login(email, password)
    }

    @Order(30)
    @Test
    fun `configure data store`() {
        click("goto-data-stores")
        assertTrue { driver.findElements(By.name("btn-configure-data-store")).isEmpty() }
        click("btn-add-new")
        typeIn("new-name", dataStoreName)
        click("btn-add-new-confirm")
        click("btn-configure-data-store")
        click("btn-add-data-connector")
        expand("header-specify-connection-properties")
        typeIn("connection-name", connectorName)
        typeIn("postgresql-server", dbContainer.host)
        typeIn("postgresql-port", dbContainer.port.toString())
        typeIn("postgresql-username", dbContainer.username)
        typeIn("postgresql-password", dbContainer.password)
        typeIn("postgresql-database", dbContainer.databaseName)
        click("btn-create-data-connector")
        acknowledgeSnackbar()
        assertTrue { byText(connectorName).isDisplayed }
    }

    @Order(40)
    @Test
    fun `add automatic etl process`() {
        val etlProcessName = "etlProcess${UUID.randomUUID()}"
        click("btn-add-automatic-etl-process")
        typeIn("process-name", etlProcessName)
        openVuetifyDropDown("selected-data-connector-id")
        selectVuetifyDropDownItem(connectorName)
        doubleClickSvgElement("public.eban")
        doubleClickSvgElement("public.ekko")
        click("btn-create-etl-process-configuration")
        acknowledgeSnackbar()
        assertTrue { byText(etlProcessName).isDisplayed }
    }

    @Order(60)
    @Test
    fun `insert data`() {
        Thread.sleep(1000)  //Wait for Debezium to kick in
        dbContainer.createConnection("").use { connection ->
            connection.createStatement().use { stmt ->
                stmt.execute(
                    """insert into EBAN VALUES(1, 'be1');
                        insert into EKET VALUES(1, 1, 'ae1');
                        update EBAN set text='be2' where id=1;
                        update EKET set text='ae2' where id=1;
                        insert into EKKO VALUES(1, 1, 'ce1');
                        insert into EKPO VALUES(1, 1, 'de1');
                        insert into EKPO VALUES(2, 1, 'de2');
                       insert into EKKO VALUES(2, 1, 'ce2');
                       insert into EKPO VALUES(3, 2, 'de3');
                       insert into EBAN VALUES(2, 'be3');
                       insert into EKET VALUES(2, 2, 'ae3');
                       insert into EKKO VALUES(3, 2, 'ce3');
                       insert into EKPO VALUES(4, 3, 'de4');
                    """.trimIndent()
                )
            }
        }
        // This is an auxiliary function disguised as a test to ensure it is executed in the correct moment - hence no assert of any sort
    }

    @Order(70)
    @Test
    fun `retrieve log identity_id`() {
        click("btn-show-etl-process-details")
        wait.until { byXpath("//td[text()[contains(.,'identity:id')]]/following-sibling::td").text.isNotEmpty() }
        logIdentityId = byXpath("//td[text()[contains(.,'identity:id')]]/following-sibling::td").text.trim()
        assertTrue { logIdentityId.isUUID() }
        click("btn-process-details-dialog-cancel")
        click("btn-close-configuration")
    }

    @Order(80)
    @Test
    fun `PQL query`() {
        click("goto-pql-interpreter")
        typeIn("query", "where l:identity:id=$logIdentityId")
        click("btn-submit-query")
        wait.until { driver.findElements(By.className("fa-plus-square-o")).isNotEmpty() }
        recorder?.take()
        with(driver.findElements(By.className("fa-plus-square-o")).filter { it.isDisplayed && it.isDisplayed }) {
            assertTrue { size == 1 }
            forEach { click(it) }
        }
        recorder?.take()
        with(driver.findElements(By.className("fa-plus-square-o")).filter { it.isDisplayed && it.isDisplayed }) {
            assertTrue { size == 3 }
            forEach { click(it) }
        }
        recorder?.take()
        val headers = driver.findElements(By.xpath("//*[@name='xes-data-table']//thead/tr/th")).withIndex()
            .associate { it.value.text to it.index }
        val dbTextColumn = headers["db:text"]
        assertNotNull(dbTextColumn)
        val rows = driver.findElements(By.xpath("//*[@name='xes-data-table']//tbody/tr"))
            .filter { it.findElements(By.tagName("td")).size >= 2 }
        assertEquals(12, rows.size)
        val log = ArrayList<ArrayList<String>>()
        for (tr in rows) {
            val cells = tr.findElements(By.tagName("td"))
            val dbText = if (dbTextColumn < cells.size) cells[dbTextColumn].text else ""
            if (dbText.isEmpty()) {
                if (log.isEmpty() || log.last().isNotEmpty())
                    log.add(ArrayList())
            } else
                log.last().add(dbText.trim('"'))
        }
        assertEquals(
            setOf(
                listOf("be1", "be2", "ce1"), listOf("be1", "be2", "ce2"), listOf("be3", "ce3")
            ), log.toSet()
        )
    }
    // endregion
}
