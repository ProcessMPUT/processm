package processm.selenium

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class I18nPlToEnTest : I18nTest("pl-PL") {
    override val targetLanguage = en
    override val loginFormTargetLanguage = loginFormEn
    override val loginFormDefaultLanguage = loginFormPl
    override val workspacesTargetLanguage = workspacesEn
    override val workspacesDefaultLanguage = workspacesPl
}