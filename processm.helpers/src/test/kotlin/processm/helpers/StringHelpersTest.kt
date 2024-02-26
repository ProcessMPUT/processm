package processm.helpers

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class StringHelpersTest {
    @Test
    fun `empty string remains empty`() {
        assertEquals("", "".toSuperscript())
        assertEquals("", "".toSubscript())
    }

    @Test
    fun `superscipt digits`() {
        assertEquals("\u2070\u00B9\u00B2\u00B3\u2074\u2075\u2076\u2077\u2078\u2079", "0123456789".toSuperscript())
        assertEquals("⁰¹²³⁴⁵⁶⁷⁸⁹", "0123456789".toSuperscript())
    }

    @Test
    fun `subscript digits`() {
        assertEquals("\u2080\u2081\u2082\u2083\u2084\u2085\u2086\u2087\u2088\u2089", "0123456789".toSubscript())
        assertEquals("₀₁₂₃₄₅₆₇₈₉", "0123456789".toSubscript())
    }

    @Test
    fun `superscript latin uppercase alphabet`() {
        assertEquals("ᴬᴮᶜᴰᴱᴳᴴᴵᴶᴷᴸᴹᴺᴼᴾᴿˢᵀᵁⱽᵂˣYᶻ", "ABCDEGHIJKLMNOPRSTUVWXYZ".toSuperscript())
    }

    @Test
    @Ignore("This test fails on purpose, see comment in the StringHelpers.kt")
    fun `superscript latin uppercase FQ`() {
        assertEquals("\uA7F3\uA7F4", "FQ".toSuperscript())
    }

    @Test
    fun `superscript latin lowercase alphabet`() {
        assertEquals("ᵃᵇᶜᵈᵉᶠᵍʰⁱʲᵏˡᵐⁿᵒᵖ\uD801\uDFA5ʳˢᵗᵘᵛʷˣʸᶻ", "abcdefghijklmnopqrstuvwxyz".toSuperscript())
    }

    @Test
    fun `subscript latin uppercase alphabet`() {
        assertEquals("ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘ\uA7AFʀꜱᴛᴜᴠᴡxʏᴢ", "ABCDEFGHIJKLMNOPQRSTUVWXYZ".toSubscript())
    }

    @Test
    fun `subscript latin lowercase alphabet`() {
        assertEquals("ₐꙺc\uD81B\uDF74ₑfgₕᵢⱼₖₗₘₙₒₚqᵣₛₜᵤᵥwₓyz", "abcdefghijklmnopqrstuvwxyz".toSubscript())
    }
}
