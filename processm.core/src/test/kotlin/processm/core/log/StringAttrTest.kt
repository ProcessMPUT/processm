package processm.core.log

import kotlin.random.Random
import kotlin.test.*

internal class StringAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1: StringAttr = StringAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = (1..3).map { allowedStringCharacters.random(Random(120)) }.joinToString("")
    )
    private val attr2: StringAttr = StringAttr(
        key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
        value = (1..3).map { allowedStringCharacters.random(Random(120)) }.joinToString("")
    )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Field value as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.value, attr2.value)
        assertSame(attr1.value, attr2.value)
    }
}
