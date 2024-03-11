package processm.selenium

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.openqa.selenium.*
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.interactions.Actions
import org.openqa.selenium.support.ui.WebDriverWait
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.lifecycle.Startables
import org.testcontainers.utility.DockerImageName
import processm.core.esb.EnterpriseServiceBus
import processm.core.loadConfiguration
import processm.core.persistence.Migrator
import processm.core.persistence.connection.DatabaseChecker
import java.time.Duration
import kotlin.random.Random
import kotlin.test.assertNotNull


val <T : PostgreSQLContainer<T>?> PostgreSQLContainer<T>.port: Int
    get() = getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT)

fun WebElement.hasCSSClass(clazz: String): Boolean =
    getAttribute("class").split(Regex("\\s+")).any { it.equals(clazz, true) }

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
    protected val headless: Boolean = true,
    /**
     * The default language for the ProcessM UI. It is passed to the driver via the preference key `intl.accept_languages`
     * to the driver, and then to the server via the Accept-Language header. The preference key seems to be lacking in
     * official documentation, however, it seems it should be formatted as a comma-separated list of language codes
     * following BCP 47.
     */
    protected val language: String? = "en-GB"
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
    fun byName(name: String) = checkNotNull(wait.until { driver.findElement(By.name(name)) })

    fun byXpath(xpath: String) = checkNotNull(wait.until { driver.findElement(By.xpath(xpath)) })

    fun byText(text: String): WebElement {
        require('\'' !in text) { "Apostrophes are currently not supported" }
        return byXpath("//*[text()='$text']")
    }

    fun byPartialText(text: String): WebElement {
        require('\'' !in text) { "Apostrophes are currently not supported" }
        return byXpath("//*[contains(text(),'$text')]")
    }

    fun assertNoText(text: String) =
        assertThrows<NoSuchElementException> { driver.findElement(By.xpath("//*[contains(text(),'$text')]")) }

    fun typeIn(name: String, value: String, replace: Boolean = true) = typeIn(byName(name), value, replace)

    fun typeIn(element: WebElement, value: String, replace: Boolean = true) {
        val n = 2
        repeat(n) { ctr ->
            try {
                with(element) {
                    wait.until { isDisplayed }
                    wait.until { isEnabled }
                    if (replace)
                        sendKeys(Keys.END)
                    while (getAttribute("value") != "") {
                        sendKeys(Keys.BACK_SPACE);
                    }
                    sendKeys(value)
                    recorder?.take()
                }
                return
            } catch (e: InvalidElementStateException) {
                if (ctr < n - 1) {
                    Thread.sleep(500)
                } else
                    throw e
            }
        }
    }

    fun click(getter: (WebDriver) -> WebElement?) {
        val element = wait.until(getter)
        assertNotNull(element)
        click(element)
    }

    fun click(element: WebElement) {
        val n = 5
        repeat(n) { ctr ->
            try {
                with(element) {
                    wait.until { isDisplayed }
                    wait.until { isEnabled }
                    driver.executeScript("arguments[0].scrollIntoView();", this)
                    click()
                    recorder?.take()
                }
                return
            } catch (e: ElementClickInterceptedException) {
                if (ctr < n - 1) {
                    // Sometimes an overlay obscures the element and Selenium refuses to click it.
                    // Interestingly enough it seems Selenium somehow "sees" the overlay even though it disappeared from the view.
                    // The following combination of movement and scrolling seems to help Selenium to refresh the view.
                    // Interestingly enough, neither (0, 0) nor (viewportWidth-1, viewportHeight-1) seem to work as the move target, hence randomization.
                    // However, it is unknown how robust this solution is.

                    //The way to retrieve viewportWidth and viewportHeight was contributed by ChatGPT.
                    val viewportWidth =
                        (driver as JavascriptExecutor).executeScript("return Math.max(document.documentElement.clientWidth, window.innerWidth || 0);") as Long
                    val viewportHeight =
                        (driver as JavascriptExecutor).executeScript("return Math.max(document.documentElement.clientHeight, window.innerHeight || 0);") as Long

                    val x = Random.Default.nextInt(viewportWidth.toInt())
                    val y = Random.Default.nextInt(viewportHeight.toInt())

                    Actions(driver)
                        .moveToLocation(x, y)
                        .scrollByAmount(100, 100).build().perform()

                    Thread.sleep((ctr + 1) * 200L)
                } else
                    throw e
            }
        }
    }

    fun click(by: By) =
        click(checkNotNull(wait.until { driver.findElements(by).singleOrNull { it.isDisplayed && it.isEnabled } }))

    fun click(name: String) = click(By.name(name))

    /**
     * To open a v-expansion-panel
     */
    fun expand(name: String) {
        require('\'' !in name) { "Apostrophes are currently not supported" }
        click(By.xpath("//*[@name='$name']/.."))
    }

    fun toggleCheckbox(name: String) {
        with(byName(name).findElement(By.xpath(".."))) {
            click()
            recorder?.take()
        }
    }

    fun openVuetifyDropDown(name: String) {
        click(byName(name).findElement(By.xpath("..")).findElement(By.cssSelector("div.v-input__append-inner")))
        recorder?.take()
    }

    fun selectVuetifyDropDownItem(vararg text: String, partial: Boolean = false) {
        val transform =
            if (partial)
                fun(element: String): String { return "contains(text(), '$element')" }
            else
                fun(element: String): String { return "text()='$element'" }
        val attributes = text.joinToString(separator = " or ", transform = transform)
        click(By.xpath("""//div[@role='listbox']//div[$attributes]"""))
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

    fun clickButtonInRow(cellText: String, buttonName: String) {
        require('\'' !in cellText) { "Apostrophes are not supported" }
        require('\'' !in buttonName) { "Apostrophes are not supported" }
        click(By.xpath("//*[text()='$cellText']/ancestor-or-self::tr//button[@name='$buttonName']"))
    }


    protected fun waitForText(text: String, tag: String = "*") {
        require('\'' !in text) { "Apostrophes are not supported" }
        wait.until { driver.findElements(By.xpath("//$tag[text()='$text']"))?.firstOrNull()?.isDisplayed }
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
                        DatabaseChecker.databaseConnectionURLProperty,
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
            if (language !== null)
                setExperimentalOption("prefs", mapOf("intl.accept_languages" to language))
        })
        driver.manage().timeouts().implicitlyWait(Duration.ofMillis(1000))
        if (recordSlideshow) recorder = VideoRecorder(driver)
        wait = WebDriverWait(driver, Duration.ofSeconds(15))
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
        if (headless)
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

    // region ProcessM-specific helpers

    protected fun register(email: String, password: String, organization: String? = null) {
        click("btn-register")
        typeIn("user-email", email)
        typeIn("user-password", password)
        if (organization !== null) {
            toggleCheckbox("new-organization")
            typeIn("organization-name", organization)
        }
        click("btn-register")
        acknowledgeSnackbar("info")
    }

    protected fun login(email: String, password: String, org: String? = null) {
        typeIn("username", email)
        typeIn("password", password)
        click("btn-login")
        if (org !== null) {
            openVuetifyDropDown("combo-organization")
            selectVuetifyDropDownItem(org)
            click("btn-select-organization")
        }
        wait.until { driver.findElements(By.name("btn-profile")).isNotEmpty() }
        with(byName("btn-profile")) {
            wait.until { isDisplayed }
            click()
        }
        with(byName("btn-logout")) {
            wait.until { isDisplayed }
        }
    }

    protected fun logout() {
        click("btn-profile")
        click("btn-logout")
        wait.until { byName("btn-login").isDisplayed }
    }

    /**
     * Requires the ACL editor to be already open
     */
    protected fun addACE(group: String, vararg role: String) {
        click("btn-acl-dialog-add-new")
        openVuetifyDropDown("ace-editor-group")
        selectVuetifyDropDownItem(group)
        openVuetifyDropDown("ace-editor-role")
        selectVuetifyDropDownItem(*role)
        click("btn-ace-editor-submit")
    }

    protected fun closeACLEditor() = click("btn-acl-dialog-close")

    protected fun addNewUserToOrganization(email: String, role: String) {
        click("btn-add-new-user")
        with(checkNotNull(wait.until { driver.findElements(By.id("newOrgMemberForm"))?.firstOrNull() })) {
            with(checkNotNull(wait.until { findElements(By.xpath(".//div[@role='combobox']"))?.firstOrNull() })) {
                click { this }
                with(checkNotNull(wait.until { findElements(By.xpath(".//input[@type='text']"))?.firstOrNull() })) {
                    sendKeys(email)
                }
            }
            openVuetifyDropDown("new-role")
            selectVuetifyDropDownItem(role)
        }
        click("btn-commit-add-member")
        acknowledgeSnackbar("info")
        wait.until {
            driver.findElements(By.xpath("//td[text()[contains(.,'$email')]]")).isNotEmpty()
        }
    }

    // endregion
}
