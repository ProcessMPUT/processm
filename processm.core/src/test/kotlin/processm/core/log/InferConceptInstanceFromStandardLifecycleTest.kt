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
