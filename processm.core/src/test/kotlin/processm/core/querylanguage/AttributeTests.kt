package processm.core.querylanguage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttributeTests {

    @Test
    fun unicodeCustomAttributeTest() {
        val attribute = Attribute(
            "[Ոչ ոք չի սիրում ցավը հենց այդպիսին, ոչ ոք չի փնտրում այն և չի տենչում հենց նրա համար, որ դա ցավ է..]",
            0,
            0
        )
        assertEquals("", attribute.hoistingPrefix)
        assertEquals(Scope.Event, attribute.scope)
        assertEquals(Scope.Event, attribute.effectiveScope)
        assertEquals(
            "Ոչ ոք չի սիրում ցավը հենց այդպիսին, ոչ ոք չի փնտրում այն և չի տենչում հենց նրա համար, որ դա ցավ է..",
            attribute.name
        )
        assertEquals("", attribute.standardName)
        assertFalse(attribute.isStandard)
        assertFalse(attribute.isClassifier)
        assertTrue(attribute.isTerminal)
    }

    @Test
    fun specialCharactersCustomAttributeTest() {
        val attribute = Attribute(
            "[^trace:!@#$%^&*():-=]",
            0,
            0
        )
        assertEquals("^", attribute.hoistingPrefix)
        assertEquals(Scope.Trace, attribute.scope)
        assertEquals(Scope.Log, attribute.effectiveScope)
        assertEquals("!@#\$%^&*():-=", attribute.name)
        assertEquals("", attribute.standardName)
        assertFalse(attribute.isStandard)
        assertFalse(attribute.isClassifier)
        assertTrue(attribute.isTerminal)
    }
}