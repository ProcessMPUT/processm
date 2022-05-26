package processm.conformance.conceptdrift

import processm.conformance.alignment
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NaiveDriftDetectorTest {

    @Test
    fun `sudden - detected`() {
        val before = alignment {
            cost = 0
        }
        val after = alignment {
            cost = 2
        }
        val detector = NaiveDriftDetector(10, .2)
        detector.fit((0 until 20).map { before })
        assertFalse(detector.drift)
        assertFalse(detector.observe(before))
        assertFalse(detector.observe(after))
        assertFalse(detector.observe(before))
        assertFalse(detector.observe(before))
        assertFalse(detector.observe(after))
        assertTrue(detector.observe(after))
    }

    @Test
    fun `gradual - not detected`() {
        val before = alignment {
            cost = 0
        }
        val after = alignment {
            cost = 2
        }
        val detector = NaiveDriftDetector(100, .2)
        detector.fit((0 until 200).map { before })
        assertFalse(detector.drift)
        for (i in 99 downTo 1) {
            (0 until i).forEach { assertFalse(detector.observe(before)) }
            (0 until 100 - i).forEach { assertFalse(detector.observe(after)) }
        }
    }

    @InMemoryXESProcessing
    fun blah() {
        val labour =
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(File("xes-logs/activities_of_daily_living_of_several_individuals-edited_hh104_weekends.xes.gz").inputStream()))).first()
        val weekends =
            HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(File("xes-logs/activities_of_daily_living_of_several_individuals-edited_hh104_labour.xes.gz").inputStream()))).first()
    }

}