package processm.selenium

import junit.framework.TestCase.assertNotNull
import org.jgroups.util.UUID
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.assertThrows
import org.openqa.selenium.By
import kotlin.test.Test
import kotlin.test.assertTrue

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class DataStoresACLTest : SeleniumBase() {

    private val email1 = "u1-${UUID.randomUUID()}@example.com"
    private val email2 = "u2-${UUID.randomUUID()}@example.com"
    private val passwords = mapOf(email1 to "Cyocats`WatIv4", email2 to "1ToWreyll)")
    private val organization = UUID.randomUUID().toString()
    private val dataStores = (1..3).map { "ds$it-${UUID.randomUUID()}" }
    private val connectorName1a = "cn1a-${UUID.randomUUID()}"
    private val connectorName1b = "cn1b-${UUID.randomUUID()}"
    private val connectorName2a = "cn2a-${UUID.randomUUID()}"
    private val connectorName2b = "cn2b-${UUID.randomUUID()}"
    private val connectionString = "jdbc:test://"

    private var currentUser: String? = null
    private var currentPage: String? = null

    protected fun iam(user: String?, page: String? = null) {
        if (currentUser != user) {
            if (currentUser !== null)
                logout()
            if (user !== null)
                login(user, passwords[user]!!)
            currentUser = user
            currentPage = null
        }
        if (page !== null && currentPage !== page) {
            click(page)
            currentPage = page
        }
    }

    private fun openACLEditor(datastore: String) {
        clickButtonInRow(datastore, "btn-data-store-security")
    }

    @Order(10)
    @Test
    fun `register with new organization`() {
        register(email1, passwords[email1]!!, organization)
    }

    @Order(10)
    @Test
    fun `register user 2`() {
        register(email2, passwords[email2]!!)
    }

    @Order(50)
    @Test
    fun `user 1 creates data stores`() {
        iam(email1, "goto-data-stores")
        assertTrue { driver.findElements(By.name("btn-configure-data-store")).isEmpty() }
        for (name in dataStores) {
            click("btn-add-new")
            typeIn("new-name", name)
            click("btn-add-new-confirm")
            wait.until { byText(name).isDisplayed }
        }
    }

    @Order(60)
    @Test
    fun `user 2 doesn't see the created data stores`() {
        iam(email2, "goto-data-stores")
        waitForText("No data available")
        assertThrows<org.openqa.selenium.NoSuchElementException> { byText(dataStores[0]) }
        assertThrows<org.openqa.selenium.NoSuchElementException> { byText(dataStores[1]) }
        assertThrows<org.openqa.selenium.NoSuchElementException> { byText(dataStores[2]) }
    }

    @Order(70)
    @Test
    fun `user 1 adds user 2 to the organization as a writer`() {
        iam(email1, "goto-users")
        click("btn-add-new-user")
        with(driver.findElement(By.id("newOrgMemberForm"))) {
            with(findElement(By.xpath(".//div[@role='combobox']"))) {
                click()
                with(findElement(By.xpath(".//input[@type='text']"))) {
                    sendKeys(email2)
                }
            }
            openVuetifyDropDown("new-role")
            selectVuetifyDropDownItem("writer")
        }
        click("btn-commit-add-member")
        acknowledgeSnackbar("info")
        wait.until {
            driver.findElements(By.xpath("//td[text()[contains(.,'$email2')]]")).isNotEmpty()
        }
    }

    @Order(75)
    @Test
    fun `user 1 adds shared group to all datastores as a reader`() {
        iam(email1, "goto-data-stores")
        for (ds in dataStores) {
            openACLEditor(ds)
            addACE(organization, "Reader", "Czytelnik")
            closeACLEditor()
        }
    }

    @Order(80)
    @Test
    fun `user 2 can see the created data stores`() {
        iam(email2, "goto-data-stores")
        wait.until { byText(dataStores[0]).isDisplayed }
        wait.until { byText(dataStores[1]).isDisplayed }
        wait.until { byText(dataStores[2]).isDisplayed }
    }

    @Order(90)
    @Test
    fun `user 2 cannot access the security details of any of the datastores`() {
        iam(email2, "goto-data-stores")
        for (dataStoreId in dataStores) {
            clickButtonInRow(dataStoreId, "btn-data-store-security")
            acknowledgeSnackbar("error")
        }
    }

    @Order(90)
    @Test
    fun `user 2 cannot delete any of the datastores`() {
        iam(email2, "goto-data-stores")
        for (dataStoreId in dataStores) {
            clickButtonInRow(dataStoreId, "btn-delete-data-store")
            // FIXME This contains language dependent string. It may break once #188 is resolved.
            val element = wait.until {
                driver.findElements(By.xpath("//div[@role='dialog']//button//*[text()[contains(.,'Yes')]]"))
                    .firstOrNull { it.isDisplayed }
            }
            assertNotNull(element)
            click(element!!)
            acknowledgeSnackbar("error")
        }
    }

    @Order(90)
    @Test
    fun `user 2 cannot rename any of the datastores`() {
        iam(email2, "goto-data-stores")
        for (dataStoreId in dataStores) {
            clickButtonInRow(dataStoreId, "btn-rename-data-store")
            click("btn-rename-dialog-submit")
            acknowledgeSnackbar("error")
        }
    }

    @Order(125)
    @Test
    fun `user 1 adds user 2 to datastore0 as an owner and removes their own access`() {
        iam(email1, "goto-data-stores")
        openACLEditor(dataStores[0])
        addACE(email2, "Owner", "Właściciel")
        clickButtonInRow(email1, "btn-remove-ace")
        // The editor disappears on its own, hence it is not necessary to close it
        openACLEditor(dataStores[0])
        acknowledgeSnackbar("error")
    }

    @Order(130)
    @Test
    fun `user 1 adds user 2 to datastore1 as a writer but fails to remove their own access`() {
        iam(email1, "goto-data-stores")
        openACLEditor(dataStores[1])
        addACE(email2, "Writer", "Pisarz")
        clickButtonInRow(email1, "btn-remove-ace")
        acknowledgeSnackbar("error")
        closeACLEditor()
    }

    @Order(140)
    @Test
    fun `user 1 adds user 2 to datastore2 as an owner and edits to a reader`() {
        iam(email1, "goto-data-stores")
        openACLEditor(dataStores[2])
        addACE(email2, "Owner", "Właściciel")
        clickButtonInRow(email2, "btn-edit-ace")
        openVuetifyDropDown("ace-editor-role")
        selectVuetifyDropDownItem("Reader", "Czytelnik")
        click("btn-ace-editor-submit")
        closeACLEditor()
    }

    @Order(160)
    @Test
    fun `user 2 can see security of datastore0`() {
        iam(email2, "goto-data-stores")
        openACLEditor(dataStores[0])
        wait.until {
            driver.findElements(By.xpath("//td[text()='$email2']/../td[text()[contains(.,'Owner')] or text()[contains(.,'Właściciel')]]"))
                ?.singleOrNull()?.isDisplayed
        }
        closeACLEditor()
    }

    @Order(170)
    @Test
    fun `user 2 cannot access the security details of datastores 1 and 2`() {
        iam(email2, "goto-data-stores")
        clickButtonInRow(dataStores[1], "btn-data-store-security")
        acknowledgeSnackbar("error")
        clickButtonInRow(dataStores[2], "btn-data-store-security")
        acknowledgeSnackbar("error")
    }


    @Order(180)
    @Test
    fun `user 2 fails to add a data connector to datastore2`() {
        iam(email2, "goto-data-stores")
        val connectionName = "other connection name"
        val connectionString = "jdbc:test://"
        clickButtonInRow(dataStores[2], "btn-configure-data-store")
        click("btn-add-data-connector")
        typeIn("connection-string-connection-name", connectionName)
        typeIn("connection-string", connectionString)
        // We click the button twice, because the first click is somehow terminated due to v-text-field hiding its hint.
        // It could be replaced with clicking anywhere outside the text field so it loses focus.
        // I think this is a bug in VUE.
        click("btn-create-data-connector")
        click("btn-create-data-connector")
        acknowledgeSnackbar("error")
        click("btn-add-data-connector-cancel")
        click("btn-close-configuration")
    }


    @Order(190)
    @Test
    fun `user 2 adds a data connector to datastore1`() {
        iam(email2, "goto-data-stores")
        clickButtonInRow(dataStores[1], "btn-configure-data-store")
        click("btn-add-data-connector")
        typeIn("connection-string-connection-name", connectorName1a)
        typeIn("connection-string", connectionString)
        // We click the button twice, because the first click is somehow terminated due to v-text-field hiding its hint.
        // It could be replaced with clicking anywhere outside the text field so it loses focus.
        // I think this is a bug in VUE.
        click("btn-create-data-connector")
        click("btn-create-data-connector")
        acknowledgeSnackbar("success")
        waitForText(connectorName1a)
    }

    @Order(200)
    @Test
    fun `user 2 renames a data connector in datastore1`() {
        clickButtonInRow(connectorName1a, "btn-data-connector-rename")
        typeIn("rename-dialog-new-name", connectorName1b)
        click("btn-rename-dialog-submit")
        waitForText(connectorName1b)
        click("btn-close-configuration")
    }


    @Order(210)
    @Test
    fun `user 1 adds a data connector to datastore2`() {
        iam(email1, "goto-data-stores")
        clickButtonInRow(dataStores[2], "btn-configure-data-store")
        click("btn-add-data-connector")
        typeIn("connection-string-connection-name", connectorName2a)
        typeIn("connection-string", connectionString)
        // We click the button twice, because the first click is somehow terminated due to v-text-field hiding its hint.
        // It could be replaced with clicking anywhere outside the text field so it loses focus.
        // I think this is a bug in VUE.
        click("btn-create-data-connector")
        click("btn-create-data-connector")
        acknowledgeSnackbar("success")
        waitForText(connectorName2a)
        click("btn-close-configuration")
    }

    @Order(220)
    @Test
    fun `user 2 fails to rename a data connector in datastore2`() {
        iam(email2, "goto-data-stores")
        clickButtonInRow(dataStores[2], "btn-configure-data-store")
        clickButtonInRow(connectorName2a, "btn-data-connector-rename")
        typeIn("rename-dialog-new-name", connectorName2b)
        click("btn-rename-dialog-submit")
        waitForText(connectorName2a)
        acknowledgeSnackbar("error")
        click("btn-close-configuration")
    }

}