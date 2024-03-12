package processm.selenium

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.openqa.selenium.By
import org.openqa.selenium.StaleElementReferenceException
import org.openqa.selenium.WebElement
import java.util.*
import kotlin.test.Test
import kotlin.test.assertNull


@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class OrganizationsTest : SeleniumBase() {

    private val randomTag = UUID.randomUUID().toString().substring(0, 8)

    private val email1 = "u1-$randomTag@example.com"
    private val password1 = "Sejvad+In6"
    private val email2 = "u2-$randomTag@example.com"
    private val password2 = "Heb`quapCik6"
    private val privateOrg1 = "org1-$randomTag"
    private val subOrg1 = "sub1-$randomTag"
    private val subOrg2 = "sub2-$randomTag"
    private val privateOrg2 = "org2-$randomTag"


    private fun getEditableElement(itemValue: String, root: WebElement? = null): WebElement? {
        // This is horrible, but for some reason it seems to me xpath cannot see the value of the value attribute
        // I got the same result in the browser's console (using `document.evaluate`), thus I think this is a problem
        // with Vue/JS/Chromium, rather than Selenium/my code
        repeat(10) {
            try {
                return (root ?: driver).findElements(By.tagName("input"))
                    .singleOrNull { it.getAttribute("value") == itemValue }
            } catch (_: StaleElementReferenceException) {
                // From time to time the DOM changes while we search thorugh it, and an exception is thrown.
                // Ignore it, repeat the search and hope for the best
                // Use repeat instead of wait.until so getEditableElement can be conveniently used to test for lack of an element
            }
        }
        return null
    }

    private fun getTreeRow(itemValue: String): WebElement? {
        var element = getEditableElement(itemValue)
        while (element !== null && !(element.tagName.equals(
                "div",
                true
            ) && element.hasCSSClass("v-treeview-node"))
        ) {
            // it seems there's no explicit way to take the parent
            element = element.findElements(By.xpath("..")).singleOrNull()
        }
        return element
    }

    private fun expandRow(row: WebElement) = click { row.findElement(By.cssSelector("button.v-treeview-node__toggle")) }

    @Order(10)
    @Test
    fun `register and login`() {
        register(email1, password1, privateOrg1)
        register(email2, password2, privateOrg2)
        login(email1, password1)
    }

    @Order(20)
    @Test
    fun `user1 sees their private organization but not the other private organization`() {
        click("goto-users")
        click(By.id("tab-organizations"))
        wait.until { getEditableElement(privateOrg1) }
        assertNull(getEditableElement(privateOrg2))
    }


    @Order(30)
    @Test
    fun `user1 creates a suborganization of their private organization`() {
        val row = checkNotNull(wait.until { getTreeRow(privateOrg1) })
        click { row.findElement(By.name("btn-create-suborganization")) }
        typeIn("text-new-name", subOrg1)
        click("btn-new-dialog-submit")
        acknowledgeSnackbar("success")
        expandRow(row)
        wait.until { getTreeRow(subOrg1) }
    }

    @Order(40)
    @Test
    fun `user1 creates a top-level organization`() {
        click("btn-create-organization")
        typeIn("text-new-name", subOrg2)
        click("btn-new-dialog-submit")
        acknowledgeSnackbar("success")
        wait.until { getTreeRow(subOrg2) }
    }

    @Order(50)
    @Test
    fun `user1 attaches subOrg2 as a child of subOrg1`() {
        click { getTreeRow(subOrg2)?.findElements(By.name("btn-begin-attach"))?.firstOrNull() }
        click { driver.findElements(By.xpath("//*[contains(text(), '$subOrg1')]")).firstOrNull() }
        acknowledgeSnackbar("success")
        expandRow(checkNotNull(wait.until { getTreeRow(subOrg1) }))
    }

    @Order(60)
    @Test
    fun `user1 deletes subOrg1`() {
        click { getTreeRow(subOrg1)?.findElements(By.name("btn-remove"))?.firstOrNull() }
        acknowledgeSnackbar("success")
        wait.until {
            try {
                val row = getTreeRow(privateOrg1) ?: return@until null
                getEditableElement(subOrg2, row)
            } catch (_: StaleElementReferenceException) {
                //ignore, redo
                return@until null
            }
        }
    }

    @Order(70)
    @Test
    fun `user1 creates subOrg1 as a child of subOrg2`() {
        val row = checkNotNull(wait.until { getTreeRow(subOrg2) })
        click { row.findElement(By.name("btn-create-suborganization")) }
        typeIn("text-new-name", subOrg1)
        click("btn-new-dialog-submit")
        acknowledgeSnackbar("success")
        expandRow(row)
        wait.until { getTreeRow(subOrg1) }
    }

    @Order(80)
    @Test
    fun `user1 fails to swap subOrg1 with subOrg2`() {
        click { getTreeRow(subOrg1)?.findElement(By.name("btn-begin-attach")) }
        click { driver.findElements(By.xpath("//*[contains(text(), '$subOrg2')]")).firstOrNull() }
        acknowledgeSnackbar("error")
    }

    @Order(90)
    @Test
    fun `user1 detaches subOrg1, subOrg2 and attaches them in the original hierarchy`() {
        click { getTreeRow(subOrg2)?.findElement(By.name("btn-detach")) }
        acknowledgeSnackbar("success")
        click { getTreeRow(subOrg1)?.findElement(By.name("btn-detach")) }
        acknowledgeSnackbar("success")
        click { getTreeRow(subOrg2)?.findElement(By.name("btn-begin-attach")) }
        click { driver.findElements(By.xpath("//span[contains(text(), '$subOrg1')]")).firstOrNull() }
        acknowledgeSnackbar("success")
        click { getTreeRow(subOrg1)?.findElement(By.name("btn-begin-attach")) }
        click { driver.findElements(By.xpath("//span[contains(text(), '$privateOrg1')]")).firstOrNull() }
        acknowledgeSnackbar("success")
    }

    @Order(100)
    @Test
    fun `user1 toggles subOrg1 as private and org1 as public`() {
        click { getTreeRow(privateOrg1)?.findElement(By.name("btn-toggle-private")) }
        click { getTreeRow(subOrg1)?.findElement(By.name("btn-toggle-private")) }
    }

    @Order(260)
    @Test
    fun `user1 logs into subOrg2 and adds user2`() {
        expandRow(checkNotNull(wait.until { getTreeRow(subOrg1) }))
        click { getTreeRow(subOrg2)?.findElements(By.name("btn-login"))?.firstOrNull() }
        click(By.id("tab-users"))
        addNewUserToOrganization(email2, "Owner")
    }

    @Order(270)
    @Test
    fun `user2 logs in and is asked to choose the org`() {
        logout()
        login(email2, password2, subOrg2)
    }

    @Order(280)
    @Test
    fun `user2 can see their org, org1 and subOrg2`() {
        click("goto-users")
        click(By.id("tab-organizations"))
        wait.until { getEditableElement(privateOrg2) }
        wait.until { getEditableElement(privateOrg1) }
        assertNull(getEditableElement(subOrg1))
        wait.until { getEditableElement(subOrg2) }
    }

    @Order(290)
    @Test
    fun `user2 creates a workspace and sets a shared group as the only owner`() {
        click("goto-home")
        click("btn-create-workspace")
        click("btn-workspace-hamburger")
        click("btn-workspace-security")
        addACE(privateOrg2, "Owner")
        clickButtonInRow(email2, "btn-remove-ace")
        closeACLEditor()
    }

    @Order(300)
    @Test
    fun `user2 deletes privateOrg2 and is asked for object removal confirmation`() {
        click("goto-users")
        click(By.id("tab-organizations"))
        click { getTreeRow(privateOrg2)?.findElements(By.name("btn-remove"))?.firstOrNull() }
        click("btn-removal-dialog-submit")
        assertNull(getEditableElement(privateOrg2))
    }

}