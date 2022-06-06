package processm.core.log

import javax.xml.stream.XMLOutputFactory
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


class InferConceptInstanceFromStandardLifecycleTest {

    @Test
    fun `infer concept instance in a log using start and complete transitions`() {
        this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { stream ->
            val xes = InferConceptInstanceFromStandardLifecycle(XMLXESInputStream(stream))

            var lastConceptName = ""
            var lastConceptInstance = ""
            var inviteAdditionalReviewerCounter = 0

            for (component in xes) {
                when (component) {
                    is Log, is Trace -> {
                        lastConceptName = ""
                        lastConceptInstance = ""
                        inviteAdditionalReviewerCounter = 0
                    }
                    is Event -> {
                        if (component.conceptName == "invite additional reviewer") {
                            if (component.lifecycleTransition == "start")
                                ++inviteAdditionalReviewerCounter
                            assertEquals(inviteAdditionalReviewerCounter.toString(), component.conceptInstance)
                        }

                        if (component.conceptName == lastConceptName) {
                            assertEquals(lastConceptInstance, component.conceptInstance)
                        } else {
                            assertNotNull(component.conceptName)
                            assertNotNull(component.conceptInstance)
                            lastConceptName = component.conceptName!!
                            lastConceptInstance = component.conceptInstance!!
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `verify whether other attributes are not affected`() {
        val original = this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { stream ->
            XMLXESInputStream(stream).toList()
        }
        this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { stream ->
            val xes = InferConceptInstanceFromStandardLifecycle(XMLXESInputStream(stream))

            for ((i, component) in xes.withIndex()) {
                val orig = original[i]
                assertEquals(orig.javaClass, component.javaClass)
                assertEquals(orig.conceptName, component.conceptName)
                assertEquals(orig.identityId, component.identityId)
                when (component) {
                    is Log -> {
                        orig as Log
                        assertEquals(orig.xesVersion, component.xesVersion)
                        assertEquals(orig.xesFeatures, component.xesFeatures)
                        assertEquals(orig.lifecycleModel, component.lifecycleModel)
                        assertEquals(orig.eventClassifiers, component.eventClassifiers)
                        assertEquals(orig.traceClassifiers, component.traceClassifiers)
                        assertEquals(orig.eventGlobals, component.eventGlobals)
                        assertEquals(orig.traceGlobals, component.traceGlobals)
                        assertEquals(orig.extensions, component.extensions)
                    }
                    is Event -> {
                        orig as Event
                        assertEquals(orig.costCurrency, component.costCurrency)
                        assertEquals(orig.costTotal, component.costTotal)
                        assertEquals(orig.lifecycleState, component.lifecycleState)
                        assertEquals(orig.timeTimestamp, component.timeTimestamp)
                        assertEquals(orig.orgGroup, component.orgGroup)
                        assertEquals(orig.orgResource, component.orgResource)
                        assertEquals(orig.orgRole, component.orgRole)
                    }
                }
            }
        }
    }

    @Test
    @Ignore
    fun `print XES with inferred concept instance`() {
        val xmlFactory = XMLOutputFactory.newInstance()
        this::class.java.getResourceAsStream("/xes-logs/JournalReview-extra.xes").use { input ->
            XMLXESOutputStream(xmlFactory.createXMLStreamWriter(System.out, "utf-8")).use { out ->
                out.write(InferConceptInstanceFromStandardLifecycle(XMLXESInputStream(input)))
            }
        }
    }
}
