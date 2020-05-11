package processm.core.log.attribute

import java.util.*
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

internal class IDAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1: IDAttr =
        IDAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = UUID.randomUUID()
        )
    private val attr2: IDAttr =
        IDAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = UUID.randomUUID()
        )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }

    @Test
    fun `Attribute with 'id' as XES tag`() {
        assertEquals(attr1.xesTag, "id")
    }
}
