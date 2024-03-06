package processm.selenium

import org.junit.jupiter.api.Order
import java.util.*
import kotlin.test.Test

abstract class I18nTest(language: String) : SeleniumBase(language = language) {

    companion object {
        val en = "English"
        val pl = "Polski"
        val loginFormEn = "Login form"
        val loginFormPl = "Panel logowania"
        val workspacesEn = "Workspaces"
        val workspacesPl = "Przestrzenie robocze"
    }

    abstract val loginFormDefaultLanguage: String
    abstract val loginFormTargetLanguage: String
    abstract val workspacesDefaultLanguage: String
    abstract val workspacesTargetLanguage: String
    abstract val targetLanguage: String

    val randomKey = UUID.randomUUID().toString().substring(0, 8)
    val email = "u$randomKey@example.com"
    val password = "18Flokpip#"
    val org = "org$randomKey"


    @Test
    @Order(10)
    fun `form is in the expected language`() {
        waitForText(loginFormDefaultLanguage)
        assertNoText(loginFormTargetLanguage)
    }

    @Test
    @Order(20)
    fun `register and login`() {
        register(email, password, org)
        login(email, password)
    }

    @Test
    @Order(30)
    fun `switch language`() {
        click("btn-settings")
        assertNoText(workspacesTargetLanguage)
        byPartialText(workspacesDefaultLanguage)
        openVuetifyDropDown("combo-selected-locale")
        selectVuetifyDropDownItem(targetLanguage, partial = true)
        byPartialText(workspacesTargetLanguage)
        assertNoText(workspacesDefaultLanguage)
    }

    @Test
    @Order(40)
    fun `refresh and the language does not change`() {
        driver.navigate().refresh()
        byPartialText(workspacesTargetLanguage)
        assertNoText(workspacesDefaultLanguage)
    }

    @Test
    @Order(50)
    fun `logout and the language reverts to default`() {
        logout()
        waitForText(loginFormDefaultLanguage)
        assertNoText(loginFormTargetLanguage)
    }

    @Test
    @Order(60)
    fun `login and the language changes`() {
        login(email, password)
        byPartialText(workspacesTargetLanguage)
        assertNoText(workspacesDefaultLanguage)
    }
}