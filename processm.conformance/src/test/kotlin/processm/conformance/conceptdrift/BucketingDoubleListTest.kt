package processm.conformance.conceptdrift

import kotlin.test.Test
import kotlin.test.assertEquals

class BucketingDoubleListTest {

    @Test
    fun `add same value`() {
        with(BucketingDoubleList()) {
            assertEquals(0, size)
            assertEquals(0, totalSize)
            add(1.0)
            assertEquals(1, size)
            assertEquals(1, totalSize)
            add(1.0)
            assertEquals(1, size)
            assertEquals(2, totalSize)
        }
    }

    @Test
    fun `add different values`() {
        with(BucketingDoubleList()) {
            assertEquals(0, size)
            assertEquals(0, totalSize)
            add(1.0)
            assertEquals(1, size)
            assertEquals(1, totalSize)
            add(2.0)
            assertEquals(2, size)
            assertEquals(2, totalSize)
        }
    }

    @Test
    fun `add almost same value x2`() {
        with(BucketingDoubleList()) {
            assertEquals(0, size)
            assertEquals(0, totalSize)
            add(1.0)
            assertEquals(1, size)
            assertEquals(1, totalSize)
            add(2.0)
            assertEquals(2, size)
            assertEquals(2, totalSize)
            add(1.00001)
            assertEquals(2, size)
            assertEquals(3, totalSize)
            add(2.00001)
            assertEquals(2, size)
            assertEquals(4, totalSize)
        }
    }

    @Test
    fun `add and countUpToExclusive`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            assertEquals(0, countUpToExclusive(0))
            assertEquals(2, countUpToExclusive(1))
            assertEquals(4, countUpToExclusive(2))
            assertEquals(5, countUpToExclusive(3))
        }
    }

    @Test
    fun `add and countFrom`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            assertEquals(5, countFrom(0))
            assertEquals(3, countFrom(1))
            assertEquals(1, countFrom(2))
            assertEquals(0, countFrom(3))
        }
    }

    @Test
    fun `add ordered and flatten`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            assertEquals(listOf(1.0, 1.0, 2.0, 2.0, 3.0), flatten())
        }
    }

    @Test
    fun `add unordered and flatten`() {
        with(BucketingDoubleList()) {
            add(3.0)
            add(2.0)
            add(1.0)
            add(1.0)
            add(2.0)
            assertEquals(listOf(1.0, 1.0, 2.0, 2.0, 3.0), flatten())
        }
    }

    @Test
    fun `add and insertionIndexOf for absent values`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            //[1.0, 1.0], [2.0, 2.0], 3.0
            assertEquals(0, insertionIndexOf(0.5))
            assertEquals(1, insertionIndexOf(1.5))
            assertEquals(2, insertionIndexOf(2.5))
            assertEquals(3, insertionIndexOf(3.5))
        }
    }

    @Test
    fun `add and insertionIndexOf for present values`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            //[1.0, 1.0], [2.0, 2.0], 3.0
            assertEquals(0, insertionIndexOf(1.0))
            assertEquals(1, insertionIndexOf(2.0))
            assertEquals(2, insertionIndexOf(3.0))
        }
    }

    @Test
    fun `add and insertionIndexOf for almost-present larger values`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            //[1.0, 1.0], [2.0, 2.0], 3.0
            assertEquals(0, insertionIndexOf(1.00001))
            assertEquals(1, insertionIndexOf(2.00001))
            assertEquals(2, insertionIndexOf(3.00001))
        }
    }

    @Test
    fun `add and insertionIndexOf for almost-present smaller values`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            add(1.0)
            add(2.0)
            //[1.0, 1.0], [2.0, 2.0], 3.0
            assertEquals(0, insertionIndexOf(0.99999))
            assertEquals(1, insertionIndexOf(1.99999))
            assertEquals(2, insertionIndexOf(2.99999))
        }
    }

    @Test
    fun `add and relevantRanges no overlap`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            with(relevantRanges(0.2, 0.1).toList()) {
                assertEquals(3, size)
                assertEquals(0.8..1.1, get(0))
                assertEquals(1.8..2.1, get(1))
                assertEquals(2.8..3.1, get(2))
            }
        }
    }

    @Test
    fun `add and relevantRanges full overlap`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(3.0)
            with(relevantRanges(2.0, 2.0).toList()) {
                assertEquals(1, size)
                assertEquals(-1.0..5.0, get(0))
            }
        }
    }

    @Test
    fun `add and relevantRanges some overlap`() {
        with(BucketingDoubleList()) {
            add(1.0)
            add(2.0)
            add(5.0)
            add(8.0)
            add(9.0)
            with(relevantRanges(1.0, 1.0).toList()) {
                assertEquals(3, size)
                assertEquals(0.0..3.0, get(0))
                assertEquals(4.0..6.0, get(1))
                assertEquals(7.0..10.0, get(2))
            }
        }
    }

    @Test
    fun `merge1`() {
        val a = listOf(1.0..2.0, 3.0..4.0)
        val b = listOf(1.2..1.4, 1.5..1.7, 1.9..2.2)
        val c = listOf(1.0..2.2, 3.0..4.0)
        assertEquals(c, a.merge(b))
        assertEquals(c, b.merge(a))
    }

    @Test
    fun `merge2`() {
        val a = listOf(1.0..2.0, 3.0..4.0)
        val b = listOf(0.8..1.4, 1.5..1.7, 1.9..2.2, 2.3..2.5)
        val c = listOf(0.8..2.2, 2.3..2.5, 3.0..4.0)
        assertEquals(c, a.merge(b))
        assertEquals(c, b.merge(a))
    }
}