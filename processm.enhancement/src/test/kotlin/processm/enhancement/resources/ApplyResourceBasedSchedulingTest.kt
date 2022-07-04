package processm.enhancement.resources

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.apache.commons.math3.distribution.RealDistribution
import org.junit.jupiter.api.assertThrows
import processm.core.log.Event
import processm.core.log.Log
import processm.core.log.Trace
import processm.core.log.attribute.Attribute
import processm.core.log.attribute.Attribute.Companion.CONCEPT_NAME
import processm.core.log.attribute.Attribute.Companion.IDENTITY_ID
import processm.core.log.attribute.IDAttr
import processm.core.log.attribute.StringAttr
import processm.core.log.takeTraces
import processm.core.models.commons.Activity
import processm.enhancement.simulation.CAUSE
import processm.enhancement.simulation.MarkovSimulation
import java.time.Duration
import java.time.Instant
import java.util.*
import kotlin.test.Test
import kotlin.test.assertTrue

class ApplyResourceBasedSchedulingTest {

    @Test
    fun `resources are utilized evenly`() {
        val resourcesCount = 10
        val tracesCount = 25
        val tracesPerResourceFloor = Math.floorDiv(tracesCount, resourcesCount)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulation = mockk<MarkovSimulation> {
            every { iterator() } returns (sequenceOf(Log()) +
                    (1..tracesCount).flatMap {
                        sequenceOf(
                            Trace(),
                            Event(
                                mutableMapOf(
                                    CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                    IDENTITY_ID to IDAttr(IDENTITY_ID, UUID.randomUUID())
                                )
                            )
                        )
                    }.asSequence()).iterator()
        }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 5.0 }
        val roles = setOf("role")
        val resources = (1..resourcesCount).map {
            return@map BasicResource("Scott", roles).apply {
                mockkObject(this)
            }
        }
        val resourceBasedScheduler = ApplyResourceBasedScheduling(
            simulation,
            resources,
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate
        )

        // Generate log to consume some resources
        resourceBasedScheduler.takeTraces(tracesCount).toList()

        resources.forEach { resource ->
            verify(
                atLeast = tracesPerResourceFloor,
                atMost = tracesPerResourceFloor + 1
            ) { resource.enqueueActivity(any(), any()) }
        }
    }

    @Test
    fun `process instance occurring rate is respected`() {
        val tracesCount = 10
        val processInstanceOccurrences = arrayListOf(1.0, 10.0, 2.0, 4.0, 4.0, 2.0, 1.0, 7.0, 11.0, 3.0)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.parse("2022-07-04T15:00:00.00Z")
        val simulation = mockk<MarkovSimulation> {
            every { iterator() } returns (sequenceOf(Log()) +
                    (1..tracesCount).flatMap {
                        sequenceOf(
                            Trace(),
                            Event(
                                mutableMapOf(
                                    CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                    IDENTITY_ID to IDAttr(IDENTITY_ID, UUID.randomUUID())
                                )
                            )
                        )
                    }.asSequence()).iterator()
        }
        val processInstanceOccurringRate =
            mockk<RealDistribution> { every { sample() } returnsMany processInstanceOccurrences }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val roles = setOf("role")
        val resource = BasicResource("jarek", roles).apply { mockkObject(this) }
        val resourceBasedScheduler = ApplyResourceBasedScheduling(
            simulation,
            listOf(resource),
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        resourceBasedScheduler.takeTraces(tracesCount).toList()

        var processStartAccumulator = 0L
        (0 until tracesCount).forEach { traceIndex ->
            processStartAccumulator += processInstanceOccurrences[traceIndex].toLong()
            verify(exactly = 1) {
                resource.enqueueActivity(
                    any(),
                    simulationStartOffset + Duration.ofMinutes(processStartAccumulator)
                )
            }
        }
    }

    @Test
    fun `distribution based activity duration is respected`() {
        val tracesCount = 10
        val activityDurations = arrayListOf(1.0, 10.0, 2.0, 4.0, 4.0, 2.0, 1.0, 7.0, 11.0, 3.0)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.now()
        val simulation = mockk<MarkovSimulation> {
            every { iterator() } returns (sequenceOf(Log()) +
                    (1..tracesCount).flatMap {
                        sequenceOf(
                            Trace(),
                            Event(
                                mutableMapOf(
                                    CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                    IDENTITY_ID to IDAttr(IDENTITY_ID, UUID.randomUUID())
                                )
                            )
                        )
                    }.asSequence()).iterator()
        }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returnsMany activityDurations }
        val roles = setOf("role")
        val resource = BasicResource("jarek", roles).apply { mockkObject(this) }
        val resourceBasedScheduler = ApplyResourceBasedScheduling(
            simulation,
            listOf(resource),
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        resourceBasedScheduler.takeTraces(tracesCount).toList()

        (0 until tracesCount).forEach { traceIndex ->
            verify(atLeast = 1) {
                resource.enqueueActivity(
                    Duration.ofMinutes(activityDurations[traceIndex].toLong()),
                    any()
                )
            }
        }
    }

    @Test
    fun `throws if cannot find a resource with required role`() {
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.now()
        val simulation = mockk<MarkovSimulation> {
            every { iterator() } returns (sequenceOf(Log()) +
                    (1..1).flatMap {
                        sequenceOf(
                            Trace(),
                            Event(
                                mutableMapOf(
                                    CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                    IDENTITY_ID to IDAttr(IDENTITY_ID, UUID.randomUUID())
                                )
                            )
                        )
                    }.asSequence()).iterator()
        }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 1.0 }

        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val resource = BasicResource("marek", setOf("role1")).apply { mockkObject(this) }
        val resourceBasedScheduler = ApplyResourceBasedScheduling(
            simulation,
            listOf(resource),
            mapOf(activity.name to setOf("antek", "role2")),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        assertThrows<NoSuchElementException> { resourceBasedScheduler.takeTraces(1).toList() }
    }

    @Test
    fun `adding critical resources shortens the total execution time`() {
        val tracesCount = 100
        val simulationStartOffset = Instant.parse("2022-07-04T15:00:00.00Z")
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val criticalActivity = mockk<Activity> { every { name } returns "activity2" }
        val simulation = mockk<MarkovSimulation> {
            every { iterator() } answers {
                (sequenceOf(Log()) +
                        (1..tracesCount).flatMap {
                            sequenceOf(
                                Trace(),
                                Event(
                                    mutableMapOf(
                                        CONCEPT_NAME to StringAttr(CONCEPT_NAME, criticalActivity.name),
                                        IDENTITY_ID to IDAttr(IDENTITY_ID, UUID(0L, 1L))
                                    )
                                ),
                                Event(
                                    mutableMapOf(
                                        CONCEPT_NAME to StringAttr(CONCEPT_NAME, activity.name),
                                        IDENTITY_ID to IDAttr(IDENTITY_ID, UUID(0L, 2L)),
                                        Attribute.CAUSE to IDAttr(Attribute.CAUSE, UUID(0L, 1L))
                                    )
                                )
                            )
                        }.asSequence()).iterator()
            }
        }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val criticalActivityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 3.0 }
        val roles = setOf("role")
        val criticalRoles = setOf("critical-role")
        val activitiesRoles = mapOf(activity.name to roles, criticalActivity.name to criticalRoles)
        val activitiesDurations = mapOf(
            activity.name to activityDurationDistribution,
            criticalActivity.name to criticalActivityDurationDistribution
        )

        val schedulerWithCriticalResource = ApplyResourceBasedScheduling(
            simulation,
            listOf(
                BasicResource("jaro", roles),
                BasicResource("andro", criticalRoles),
                BasicResource("antek", criticalRoles),
                BasicResource("mati", criticalRoles)
            ),
            activitiesRoles,
            activitiesDurations,
            processInstanceOccurringRate,
            simulationStartOffset
        )
        val schedulerWithNoCriticalResource = ApplyResourceBasedScheduling(
            simulation,
            listOf(BasicResource("jaro", roles), BasicResource("andro", criticalRoles)),
            activitiesRoles,
            activitiesDurations,
            processInstanceOccurringRate,
            simulationStartOffset
        )

        val simulationWithCriticalResourceEnd =
            (schedulerWithCriticalResource.takeTraces(tracesCount).last() as Event).timeTimestamp
        val simulationWithNoCriticalResourceEnd =
            (schedulerWithNoCriticalResource.takeTraces(tracesCount).last() as Event).timeTimestamp

        val simulationWithCriticalResourceDuration =
            Duration.between(simulationStartOffset, simulationWithCriticalResourceEnd)
        val simulationWithNoCriticalResourceDuration =
            Duration.between(simulationStartOffset, simulationWithNoCriticalResourceEnd)

        assertTrue(simulationWithNoCriticalResourceDuration.toMinutes() / simulationWithCriticalResourceDuration.toMinutes() >= 2)
    }
}
