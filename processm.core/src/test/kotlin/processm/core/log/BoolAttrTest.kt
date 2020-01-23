package processm.core.log

import kotlin.random.Random
import kotlin.test.*

internal class BoolAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1: BoolAttr = BoolAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = true
    )
    private val attr2: BoolAttr = BoolAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = false
    )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Field value with the correct value`() {
        assertEquals(attr1.value, true)
        assertEquals(attr2.value, false)
    }
}
