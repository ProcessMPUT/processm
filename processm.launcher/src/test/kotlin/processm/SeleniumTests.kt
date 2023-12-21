package processm

import org.jgroups.util.UUID
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.OutputType
import org.openqa.selenium.TakesScreenshot
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.isUUID
import processm.core.helpers.loadConfiguration
import processm.core.persistence.Migrator
import java.nio.file.FileSystems
import java.nio.file.Files
import java.time.Duration
import kotlin.random.Random
import kotlin.test.*

class VideoRecorder(val obj: TakesScreenshot) {
    private var ctr = 0
    private val target = Files.createTempDirectory("processm")

    fun take() {
        val f = obj.getScreenshotAs(OutputType.FILE)
        Files.move(f.toPath(), FileSystems.getDefault().getPath(target.toString(), String.format("%04d.png", ctr)))
        ctr++
    }

    fun finish() {
        println()
        println()
        println()
        println("Screenshots were saved to $target")
        println("Go there and run: ffmpeg -framerate 2 -i %04d.png output.mp4")
        println()
        println()
        println()
    }
}

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SeleniumTests {

    /**
     * Set to true to take screenshots from time to time, and place them in a newly-created directory
     */
    private val recordSlideshow = false

    /**
     * Set to true to stop test from bringing up its own instance of ProcessM, and use an already running instance instead.
     *
     * If set to false, it will use the build version of the UI, a by-product of `mvn package`
     */
    private val useManuallyStartedServices = false


    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    private lateinit var mainDbContainer: PostgreSQLContainer<*>

    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    private lateinit var backendThread: Thread

    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    private lateinit var esb: EnterpriseServiceBus
    private lateinit var dbContainer: PostgreSQLContainer<*>
    private var httpPort: Int = -1

    val email = UUID.randomUUID().toString() + "@example.com"
    val password = "TestTest123"
    val organization = "Org${UUID.randomUUID()}"
    val dataStoreName = "TestDS"
    val connectorName = "SomeConnector"
    private lateinit var driver: ChromeDriver
    private lateinit var wait: WebDriverWait
    private var recorder: VideoRecorder? = null


    fun byName(name: String) = driver.findElement(By.name(name))

    fun byXpath(xpath: String) = driver.findElement(By.xpath(xpath))

    fun typeIn(name: String, value: String) {
        with(byName(name)) {
            wait.until { isDisplayed }
            wait.until { isEnabled }
            sendKeys(value)
            recorder?.take()
        }
    }

    fun click(name: String) {
        with(byName(name)) {
            wait.until { isDisplayed }
            wait.until { isEnabled }
            driver.executeScript("arguments[0].scrollIntoView();", this)
            click()
            recorder?.take()
        }
    }

    fun toggleCheckbox(name: String) {
        with(byName(name).findElement(By.xpath(".."))) {
            click()
            recorder?.take()
        }
    }

    fun openVuetifyDropDown(name: String) {
        byName(name).findElement(By.xpath("..")).findElement(By.cssSelector("div.v-input__append-inner")).click()
    }

    fun selectVuetifyDropDownItem(text: String) {
        byXpath("""//div[text()='${text}']""").click()
        recorder?.take()
    }

    fun doubleClickSvgElement(text: String) {
        val xpath = "//*[local-name()='tspan' and text()='$text']"
        wait.until { driver.findElements(By.xpath(xpath)).isNotEmpty() }
        val element = byXpath(xpath)
        Actions(driver)
            .moveToLocation(element.location.x + 20, element.location.y + 20)
            .doubleClick()
            .perform()
        recorder?.take()
    }

    private val <T : PostgreSQLContainer<T>?> PostgreSQLContainer<T>.port: Int
        get() = getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)

    private fun setupDB() {
        if (!useManuallyStartedServices) {
            mainDbContainer = PostgreSQLContainer(
                DockerImageName.parse("timescale/timescaledb:latest-pg12-oss")
                    .asCompatibleSubstituteFor("postgres")
            )
                .withUsername("postgres")
                .withPassword("password")
            Startables.deepStart(listOf(mainDbContainer)).join()

            val mainDbName = "processm${Random.Default.nextInt()}"
            mainDbContainer.createConnection("")
                .use { connection ->
                    connection.createStatement().use { stmt ->
                        stmt.execute("create database \"$mainDbName\"")
                    }
                }
            mainDbContainer.withDatabaseName(mainDbName)
        }
        dbContainer = PostgreSQLContainer(
            DockerImageName.parse("debezium/postgres:12")
                .asCompatibleSubstituteFor("postgres")
        )
            .withUsername("postgres")
            .withPassword("password")
        Startables.deepStart(listOf(dbContainer)).join()
    }


    private fun setupBackend() {
        if (useManuallyStartedServices)
            httpPort = 2080
        else {
            httpPort = Random.Default.nextInt(1025, 65535)
            System.setProperty("ktor.deployment.port", httpPort.toString())

            esb = EnterpriseServiceBus()
            backendThread = object : Thread() {
                override fun run() {
                    loadConfiguration(true)
                    System.setProperty(
                        "PROCESSM.CORE.PERSISTENCE.CONNECTION.URL",
                        "${mainDbContainer.jdbcUrl}&user=${mainDbContainer.username}&password=${mainDbContainer.password}"
                    )
                    Migrator.reloadConfiguration()
                    esb.apply {
                        autoRegister()
                        startAll()
                    }
                }
            }
            backendThread.start()
            Thread.sleep(10000)
        }
    }

    @BeforeAll
    fun bringUp() {
        setupDB()
        setupBackend()
    }

    @AfterAll
    fun takeDown() {
        if (!useManuallyStartedServices) {
            esb.close()
            backendThread.join()
            mainDbContainer.close()
        }
        dbContainer.close()
    }

    @BeforeTest
    fun startSelenium() {
        driver = ChromeDriver(ChromeOptions().apply { addArguments("--window-size=1920,1080") }) //, "--headless"
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500))
        if (recordSlideshow)
            recorder = VideoRecorder(driver)
        wait = WebDriverWait(driver, Duration.ofSeconds(5))
    }

    @BeforeTest
    fun prepareDB() {
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

    @AfterTest
    fun shutdownSelenium() {
        recorder?.take()
        recorder?.finish()
        driver.close()
    }

    @Test
    fun register() {
        driver.get("http://localhost:$httpPort/")
        click("btn-register")
        typeIn("user-email", email)
        typeIn("user-password", password)
        toggleCheckbox("new-organization")
        typeIn("organization-name", organization)
        click("btn-register")
        typeIn("username", email)
        typeIn("password", password)
        click("btn-login")
        click("goto-data-stores")
        assertTrue { driver.findElements(By.name("btn-configure-data-store")).isEmpty() }
        click("btn-add-new")
        typeIn("new-name", dataStoreName)
        click("btn-add-new-confirm")
        wait.until { driver.findElements(By.name("btn-configure-data-store")).isNotEmpty() }
        click("btn-configure-data-store")
        click("btn-add-data-connector")
        click("header-specify-connection-properties")
        typeIn("connection-name", connectorName)
        typeIn("postgresql-server", dbContainer.host)
        typeIn("postgresql-port", dbContainer.port.toString())
        typeIn("postgresql-username", dbContainer.username)
        typeIn("postgresql-password", dbContainer.password)
        typeIn("postgresql-database", dbContainer.databaseName)
        click("btn-create-data-connector")
        click("btn-add-automatic-etl-process")
        typeIn("process-name", "test")
        openVuetifyDropDown("selected-data-connector-id")
        selectVuetifyDropDownItem(connectorName)
        doubleClickSvgElement("eban")
        doubleClickSvgElement("ekko")
        click("btn-create-etl-process-configuration")
        Thread.sleep(5000)  //Wait for Debezium to kick in
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
            click("btn-show-etl-process-details")
            wait.until { byXpath("//td[text()[contains(.,'identity:id')]]/following-sibling::td").text.isNotEmpty() }
            val logIdentityId = byXpath("//td[text()[contains(.,'identity:id')]]/following-sibling::td").text.trim()
            assertTrue { logIdentityId.isUUID() }
            Thread.sleep(1000)  // Unsure if necessary
            click("btn-process-details-dialog-cancel")
            click("btn-close-configuration")
            click("goto-pql-interpreter")
            typeIn("query", "where l:identity:id=$logIdentityId")
            click("btn-submit-query")
            wait.until { driver.findElements(By.className("fa-plus-square-o")).isNotEmpty() }
            recorder?.take()
            with(driver.findElements(By.className("fa-plus-square-o")).filter { it.isDisplayed && it.isDisplayed }) {
                assertTrue { size == 1 }
                forEach { it.click() }
            }
            recorder?.take()
            with(driver.findElements(By.className("fa-plus-square-o")).filter { it.isDisplayed && it.isDisplayed }) {
                assertTrue { size == 3 }
                forEach { it.click() }
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
    }
}