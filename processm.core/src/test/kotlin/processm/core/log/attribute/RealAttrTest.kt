package processm.core.log.attribute

import processm.core.log.AttributeMap
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class RealAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val map = AttributeMap<Attribute<*>>()
    private val attr1: RealAttr =
        RealAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = 33.23,
            map
        )
    private val attr2: RealAttr =
        RealAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = 22.00,
            map
        )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Field value with the correct value`() {
        assertEquals(attr1.value, 33.23)
        assertEquals(attr2.value, 22.0)
    }

    @Test
    fun `Attribute with 'float' as XES tag`() {
        assertEquals(attr1.xesTag, "float")
    }
}
