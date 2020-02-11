package processm.core.log.attribute

import java.time.Instant
import java.util.*
import kotlin.random.Random
import kotlin.test.*

internal class DateTimeAttrTest {
    private val allowedStringCharacters = ('0'..'z').toList().toTypedArray()
    private val attr1: DateTimeAttr =
        DateTimeAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = Date.from(Instant.now())
        )
    private val attr2: DateTimeAttr =
        DateTimeAttr(
            key = (1..5).map { allowedStringCharacters.random(Random(100)) }.joinToString(""),
            value = Date.from(Instant.now())
        )

    @Test
    fun `Field key as String intern() field to reduce memory usage - value and reference are the same`() {
        assertEquals(attr1.key, attr2.key)
        assertSame(attr1.key, attr2.key)
    }
}
