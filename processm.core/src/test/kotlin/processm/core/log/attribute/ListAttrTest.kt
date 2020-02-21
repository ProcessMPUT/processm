package processm.core.log.attribute

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class ListAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1 = ListAttr(key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""))
    private val attr2 = ListAttr(key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""))

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Attribute with 'list' as XES tag`() {
        assertEquals(attr1.xesTag, "list")
    }
}
