package processm.core.log

import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.models.metadata.BasicMetadata.LEAD_TIME
import processm.core.models.metadata.BasicMetadata.SERVICE_TIME
import processm.core.models.metadata.BasicMetadata.SUSPENSION_TIME
import processm.core.models.metadata.BasicMetadata.WAITING_TIME
import processm.helpers.map2d.DoublingMap2D
import java.time.Duration
import java.time.Instant

/**
 * A transforming XES stream that extends every trace and event with [LEAD_TIME], [SERVICE_TIME], [WAITING_TIME], [SUSPENSION_TIME]
 * attributes in the ISO-8601 format. If events miss the [CONCEPT_INSTANCE] attribute, then all events with the same
 * [CONCEPT_NAME] are considered to belong to the same instance of an activity. Use [InferConceptInstanceFromStandardLifecycle]
 * if [CONCEPT_INSTANCE] is not set in the [base] stream.
 *
 * @exception IllegalArgumentException if an event does not contain [CONCEPT_NAME] or [TIME_TIMESTAMP] attribute.
 * @see [Duration.toString]
 */
class InferTimes(val base: XESInputStream) : XESInputStream {
    override fun iterator(): Iterator<XESComponent> = sequence {
        // key1: concept:name, key2: concept:instance, value: current timing
        val nameInstanceToEvent = DoublingMap2D<String, String?, Times>()
        var calculator: Calculator = StandardLifecycleCalculator
        var traceBuffer: Trace? = null
        val eventBuffer = ArrayList<Event>()

        suspend fun SequenceScope<XESComponent>.flushBuffer() {
            if (traceBuffer !== null) with(traceBuffer!!) {
                // set trace statistics
                val lead = Duration.between(eventBuffer.first().timeTimestamp, eventBuffer.last().timeTimestamp)
                // at this moment [nameInstanceToEvent] consists of the total service times spotted in the trace
                val service = Duration.ofMillis(nameInstanceToEvent.rows.sumOf { row ->
                    nameInstanceToEvent.getRow(row).values.sumOf { v -> v.service.toMillis() }
                })
                val waiting = Duration.ofMillis(nameInstanceToEvent.rows.sumOf { row ->
                    nameInstanceToEvent.getRow(row).values.sumOf { v -> v.waiting.toMillis() }
                })
                val suspension = Duration.ofMillis(nameInstanceToEvent.rows.sumOf { row ->
                    nameInstanceToEvent.getRow(row).values.sumOf { v -> v.suspension.toMillis() }
                })

                attributesInternal[LEAD_TIME.urn] = lead.toString()
                attributesInternal[SERVICE_TIME.urn] = service.toString()
                attributesInternal[WAITING_TIME.urn] = waiting.toString()
                attributesInternal[SUSPENSION_TIME.urn] = suspension.toString()

                yield(traceBuffer!!)
                yieldAll(eventBuffer)
                eventBuffer.clear()
                traceBuffer = null
            }
            nameInstanceToEvent.clear()
        }

        for (component in base) {
            when (component) {
                is Event -> with(component) {
                    require(!conceptName.isNullOrBlank()) { "The $CONCEPT_NAME attribute must be set and not blank for all events." }
                    requireNotNull(timeTimestamp) { "The $TIME_TIMESTAMP attribute must be set for all events." }

                    val times = nameInstanceToEvent.compute(conceptName!!, conceptInstance) { _, _, old ->
                        if (old === null) {
                            Times(timeTimestamp!!, calculator.getState(component))
                        } else {
                            val durationSinceLastEvent = Duration.between(old.lastTimestamp, timeTimestamp!!)
                            old.lead = old.lead.plus(durationSinceLastEvent)
                            when (old.state) {
                                State.Servicing -> old.service = old.service.plus(durationSinceLastEvent)
                                State.Waiting -> old.waiting = old.waiting.plus(durationSinceLastEvent)
                                State.Suspended -> old.suspension = old.suspension.plus(durationSinceLastEvent)
                                else -> Unit
                            }
                            old.state = calculator.getState(component)

                            old
                        }
                    }!!

                    attributesInternal[LEAD_TIME.urn] = times.lead.toString()
                    attributesInternal[SERVICE_TIME.urn] = times.service.toString()
                    attributesInternal[WAITING_TIME.urn] = times.waiting.toString()
                    attributesInternal[SUSPENSION_TIME.urn] = times.suspension.toString()

                    eventBuffer.add(component)
                }
                is Trace -> {
                    flushBuffer()
                    traceBuffer = component
                }
                is Log -> {
                    flushBuffer()
                    calculator =
                        if (component.lifecycleModel?.equals("bpaf", true) == true) BPAFCalculator
                        else StandardLifecycleCalculator
                    yield(component)
                }
            }
        }
        flushBuffer()
    }.iterator()
}

private enum class State {
    Waiting,
    Servicing,
    Suspended,
    Stopped
}

private class Times(
    var lastTimestamp: Instant,
    var state: State,
    var lead: Duration = Duration.ZERO,
    var service: Duration = Duration.ZERO,
    var waiting: Duration = Duration.ZERO,
    var suspension: Duration = Duration.ZERO
)

private interface Calculator {
    fun getState(event: Event): State
}

/**
 * See IEEE 1849-2016 Figure 5.
 */
private object StandardLifecycleCalculator : Calculator {
    override fun getState(event: Event): State = when (event.lifecycleTransition?.lowercase()) {
        "start", "resume" -> State.Servicing
        "schedule", "assign", "reassign" -> State.Waiting
        "suspend" -> State.Suspended
        else -> State.Stopped
    }
}

/**
 * See IEEE 1849-2016 Figure 4.
 */
private object BPAFCalculator : Calculator {
    override fun getState(event: Event): State = with(event.lifecycleState?.lowercase()) {
        if (this === null) State.Stopped
        else if (this == "open") State.Servicing
        else if (startsWith("open.running") && this != "open.running.suspended") State.Servicing
        else if (startsWith("open.notrunning") && !startsWith("open.notrunning.suspended")) State.Waiting
        else if (this == "open.running.suspended" || startsWith("open.notrunning.suspended")) State.Suspended
        else State.Stopped
    }
}
