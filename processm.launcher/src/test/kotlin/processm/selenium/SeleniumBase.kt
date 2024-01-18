package processm.selenium

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.TestInstance
import org.openqa.selenium.By
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.helpers.loadConfiguration
import processm.core.persistence.Migrator
import java.time.Duration
import kotlin.random.Random

val <T : PostgreSQLContainer<T>?> PostgreSQLContainer<T>.port: Int
    get() = getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("e2e")
abstract class SeleniumBase(
    /**
     * Set to true to take screenshots from time to time, and place them in a newly-created directory
     */
    protected val recordSlideshow: Boolean = false,

    /**
     * Set to true to stop test from bringing up its own instance of ProcessM, and use an already running instance instead.
     *
     * If set to false, it will use the build version of the UI, a by-product of `mvn package`
     */
    protected val useManuallyStartedServices: Boolean = false,

    /**
     * If true, the web browser is run in a headless mode.
     */
    protected val headless: Boolean = true
) : TestCaseAsAClass() {

    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    protected lateinit var mainDbContainer: PostgreSQLContainer<*>

    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    protected lateinit var backendThread: Thread

    /**
     * This variable will be initialized only if [useManuallyStartedServices] is set to false
     */
    protected lateinit var esb: EnterpriseServiceBus

    protected var httpPort: Int = -1
    protected lateinit var driver: ChromeDriver
    protected lateinit var wait: WebDriverWait
    protected var recorder: VideoRecorder? = null

    // region Selenium helpers
    fun byName(name: String) = driver.findElement(By.name(name))

    fun byXpath(xpath: String) = driver.findElement(By.xpath(xpath))

    fun byText(text: String): WebElement {
        require('\'' !in text) { "Apostrophes are currently not supported" }
        return driver.findElement(By.xpath("//*[text()='$text']"))
    }

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
        Actions(driver).moveToLocation(element.location.x + 20, element.location.y + 20).doubleClick().perform()
        recorder?.take()
    }

    fun acknowledgeSnackbar(snackbarColor: String = "success") {
        with(driver.findElement(By.cssSelector("div.v-snack > div.$snackbarColor"))) {
            recorder?.take()
            wait.until { isDisplayed }
            recorder?.take()
            findElement(org.openqa.selenium.By.tagName("button")).click()
            wait.until { !isDisplayed }
            recorder?.take()
        }
    }

    // endregion

    // region setup

    private fun setupBackend() {
        if (useManuallyStartedServices) httpPort = 2080
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

    private fun startSelenium() {
        driver = ChromeDriver(ChromeOptions().apply {
            addArguments("--window-size=1920,1080")
            if (headless) addArguments("--headless=new")
        })
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(500))
        if (recordSlideshow) recorder = VideoRecorder(driver)
        wait = WebDriverWait(driver, Duration.ofSeconds(5))
        driver.get("http://localhost:$httpPort/")
    }

    private fun setupMainDB() {
        if (!useManuallyStartedServices) {
            mainDbContainer = PostgreSQLContainer(
                DockerImageName.parse("timescale/timescaledb:latest-pg12-oss").asCompatibleSubstituteFor("postgres")
            ).withUsername("postgres").withPassword("password")
            Startables.deepStart(listOf(mainDbContainer)).join()

            val mainDbName = "processm${Random.Default.nextInt()}"
            mainDbContainer.createConnection("").use { connection ->
                connection.createStatement().use { stmt ->
                    stmt.execute("create database \"$mainDbName\"")
                }
            }
            mainDbContainer.withDatabaseName(mainDbName)
        }
    }

    @BeforeAll
    fun bringUp() {
        setupMainDB()
        setupBackend()
        startSelenium()
    }

    private fun shutdownSelenium() {
        recorder?.take()
        recorder?.finish()
        driver.close()
    }

    @AfterAll
    fun takeDown() {
        shutdownSelenium()
        if (!useManuallyStartedServices) {
            esb.close()
            backendThread.join()
            mainDbContainer.close()
        }

    }

    // endregion
}