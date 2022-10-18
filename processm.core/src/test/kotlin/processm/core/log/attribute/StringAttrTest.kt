package processm.core.log.attribute

import processm.core.log.AttributeMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class StringAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val map = AttributeMap<Attribute<*>>()
    private val attr1: StringAttr =
        StringAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = (1..3).map { allowedStringCharacters.random(Random(120)) }.joinToString(""),
            map
        )
    private val attr2: StringAttr =
        StringAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = (1..3).map { allowedStringCharacters.random(Random(120)) }.joinToString(""),
            map
        )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Attribute with 'string' as XES tag`() {
        assertEquals(attr1.xesTag, "string")
    }
}
