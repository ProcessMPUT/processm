package processm.core.log

import kotlin.random.Random
import kotlin.test.*

internal class RealAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1: RealAttr = RealAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = 33.23
    )
    private val attr2: RealAttr = RealAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = 22.00
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
}
