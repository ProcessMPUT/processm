package processm.selenium


import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.TestMethodOrder
import org.openqa.selenium.By
import org.openqa.selenium.Keys
import org.openqa.selenium.WebElement
import java.util.*
import kotlin.test.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class GroupsManagementAndWorkspaceACLTest : SeleniumBase() {

    private val email = "u1-${UUID.randomUUID()}@example.com"
    private val password = "af*OgImcym6"
    private val organization = "7Onashot_"
    private val group1a = "g1a-${UUID.randomUUID()}"
    private val group1b = "g1b-${UUID.randomUUID()}"
    private val group2 = "g2-${UUID.randomUUID()}"
    private lateinit var workspaceId: String

    private fun listWorkspaces(): List<String> =
        driver.findElements(By.xpath("//*[starts-with(@id, 'workspace-tab-')]")).map { it.getAttribute("id") }

    @Order(10)
    @Test
    fun `register and login`() {
        register(email, password, organization)
        login(email, password)
    }

    @Order(20)
    @Test
    fun `create group`() {
        click("goto-users")
        click(By.id("tab-groups"))
        click("btn-add-new-group")
        typeIn("text-new-group-name", group1a)
        click("btn-new-group-submit")
        acknowledgeSnackbar("info")
        assertNotNull(getRowWithEditableItem(group1a))
    }


    @Order(25)
    @Test
    fun `create group 2`() {
        click("btn-add-new-group")
        typeIn("text-new-group-name", group2)
        click("btn-new-group-submit")
        acknowledgeSnackbar("info")
        assertNotNull(getRowWithEditableItem(group2))
    }

    @Order(30)
    @Test
    fun `create workspace`() {
        click("goto-home")
        assertTrue { listWorkspaces().isEmpty() }
        click("btn-create-workspace")
        val workspaces = listWorkspaces()
        assertEquals(1, workspaces.size)
        workspaceId = workspaces.single()
    }

    @Order(40)
    @Test
    fun `add group as an owner`() {
        click(By.id(workspaceId))
        click("btn-workspace-hamburger")
        click("btn-workspace-security")
        addACE(group1a, "Właściciel", "Owner")
        closeACLEditor()
    }

    private fun getRowWithEditableItem(itemValue: String): WebElement? {
        // This is horrible, but for some reason it seems to me xpath cannot see the value of the value attribute
        // I got the same result in the browser's console (using `document.evaluate`), thus I think this is a problem
        // with Vue/JS/Chromium, rather than Selenium/my code
        var element =
            driver.findElements(By.tagName("input")).singleOrNull { it.getAttribute("value") == itemValue }
        while (element !== null && !element.tagName.equals("tr", true)) {
            // it seems there's no explicit way to take the parent
            element = element.findElements(By.xpath("..")).singleOrNull()
        }
        return element
    }

    @Order(50)
    @Test
    fun `rename group`() {
        click("goto-users")
        click(By.id("tab-groups"))
        val row = requireNotNull(wait.until { getRowWithEditableItem(group1a) })
        val input = requireNotNull(wait.until { row.findElements(By.tagName("input"))?.firstOrNull() })
        click(input)
        typeIn(input, group1b)
        click { row.findElements(By.name("btn-edit-group-submit"))?.firstOrNull() }
        acknowledgeSnackbar("info")
        assertNull(getRowWithEditableItem(group1a))
        assertNotNull(getRowWithEditableItem(group1b))
    }

    @Order(60)
    @Test
    fun `remove group successfully`() {
        click {
            getRowWithEditableItem(group1b)?.findElements(By.name("btn-remove-group"))?.firstOrNull()
        }
        acknowledgeSnackbar("info")
        assertNull(getRowWithEditableItem(group1b))
    }


    @Order(80)
    @Test
    fun `add group as an owner and remove self`() {
        click("goto-home")
        click(By.id(workspaceId))
        click("btn-workspace-hamburger")
        click("btn-workspace-security")
        addACE(group2, "Właściciel", "Owner")
        clickButtonInRow(email, "btn-remove-ace")
        // The editor disappears on its own, hence it is not necessary to close it
        acknowledgeSnackbar("error")
    }

    @Order(90)
    @Test
    fun `cannot see any workspaces after refresh`() {
        driver.navigate().refresh()
        assertTrue { listWorkspaces().isEmpty() }
    }

    @Order(100)
    @Test
    fun `fail to remove group due to insufficient permission`() {
        click("goto-users")
        click(By.id("tab-groups"))
        click {
            getRowWithEditableItem(group2)?.findElements(By.name("btn-remove-group"))?.firstOrNull()
        }
        click("btn-removal-dialog-submit")
        acknowledgeSnackbar("error")
        assertNotNull(getRowWithEditableItem(group2))
    }

    @Order(110)
    @Test
    fun `add self to group2`() {
        click {
            getRowWithEditableItem(group2)?.findElements(By.name("btn-add-group-member"))?.firstOrNull()
        }
        with(driver.findElement(By.id("newMemberForm"))) {
            with(findElement(By.xpath(".//div[@role='combobox']"))) {
                click()
                with(findElement(By.xpath(".//input[@type='text']"))) {
                    sendKeys(email)
                    sendKeys(Keys.ENTER)
                }
            }
        }
        click("btn-submit-new-group-member")
        acknowledgeSnackbar("success")
    }

    @Order(120)
    @Test
    fun `can see the workspace again`() {
        click("goto-home")
        wait.until { listOf(workspaceId) == listWorkspaces() }
    }

    @Order(130)
    @Test
    fun `remove self from the group`() {
        click("goto-users")
        click(By.id("tab-groups"))
        click {
            wait.until { getRowWithEditableItem(group2) }?.findElements(By.tagName("td"))?.first()
        }
        clickButtonInRow(email, "btn-remove-group-member")
        acknowledgeSnackbar("success")
    }

    @Order(140)
    @Test
    fun `fail to remove group due to insufficient permission again`() {
        click("goto-users")
        click(By.id("tab-groups"))
        click {
            getRowWithEditableItem(group2)?.findElements(By.name("btn-remove-group"))?.firstOrNull()
        }
        click("btn-removal-dialog-submit")
        acknowledgeSnackbar("error")
        assertNotNull(getRowWithEditableItem(group2))
    }

    @Order(150)
    @Test
    fun `add self to group2 again`() {
        click {
            getRowWithEditableItem(group2)?.findElements(By.name("btn-add-group-member"))?.firstOrNull()
        }
        with(driver.findElement(By.id("newMemberForm"))) {
            with(findElement(By.xpath(".//div[@role='combobox']"))) {
                click()
                with(findElement(By.xpath(".//input[@type='text']"))) {
                    sendKeys(email)
                    sendKeys(Keys.ENTER)
                }
            }
        }
        click("btn-submit-new-group-member")
        acknowledgeSnackbar("success")
    }

    @Order(160)
    @Test
    fun `successfully remove group with the workspace`() {
        click {
            getRowWithEditableItem(group2)?.findElements(By.name("btn-remove-group"))?.firstOrNull()
        }
        click("btn-removal-dialog-submit")
        acknowledgeSnackbar("success")
        assertNull(getRowWithEditableItem(group2))
    }

}