package processm.selenium

import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.TestMethodOrder

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class I18nEnToPlTest : I18nTest("en-US") {
    override val targetLanguage = pl
    override val loginFormDefaultLanguage = loginFormEn
    override val loginFormTargetLanguage = loginFormPl
    override val workspacesDefaultLanguage = workspacesEn
    override val workspacesTargetLanguage = workspacesPl
}