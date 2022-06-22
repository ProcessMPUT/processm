package processm.enhancement.resources

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.verify
import org.apache.commons.math3.distribution.RealDistribution
import org.junit.jupiter.api.assertThrows
import processm.core.models.commons.Activity
import processm.enhancement.simulation.ActivityInstance
import processm.enhancement.simulation.Simulation
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue

class ResourceBasedSchedulerTest {

    @Test
    fun `resources are utilized evenly`() {
        val resourcesCount = 10
        val tracesCount = 25
        val tracesPerResourceFloor = Math.floorDiv(tracesCount, resourcesCount)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulation = mockk<Simulation> { every { generateTraces() } returns (1..tracesCount).map { listOf(ActivityInstance(activity, null)) }.asSequence() }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 5.0 }
        val roles = setOf("role")
        val resources = (1..resourcesCount).map {
            return@map BasicResource(roles).apply {
                mockkObject(this)
            }
        }
        val resourceBasedScheduler = ResourceBasedScheduler(
            simulation,
            resources,
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate
        )

        resourceBasedScheduler.scheduleWith().map {
            it.values.minByOrNull { (start, stop) -> start }
        }.take(tracesCount).toList()

        resources.forEach { resource ->
            verify(atLeast = tracesPerResourceFloor, atMost = tracesPerResourceFloor + 1) { resource.enqueueActivity(any(), any()) }
        }
    }

    @Test
    fun `process instance occurring rate is respected`() {
        val tracesCount = 10
        val processInstanceOccurrences = arrayListOf(1.0, 10.0, 2.0, 4.0, 4.0, 2.0, 1.0, 7.0, 11.0, 3.0)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.now()
        val simulation = mockk<Simulation> { every { generateTraces() } returns (1..tracesCount).map { listOf(ActivityInstance(activity, null)) }.asSequence() }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returnsMany processInstanceOccurrences }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val roles = setOf("role")
        val resource = BasicResource(roles).apply { mockkObject(this) }
        val resourceBasedScheduler = ResourceBasedScheduler(
            simulation,
            listOf(resource),
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        resourceBasedScheduler.scheduleWith().map {
            it.values.minByOrNull { (start, stop) -> start }
        }.take(tracesCount).toList()

        var processStartAccumulator = 0L
        (0 until tracesCount).forEach { traceIndex ->
            processStartAccumulator += processInstanceOccurrences[traceIndex].toLong()
            verify(exactly = 1) { resource.enqueueActivity(any(), simulationStartOffset + Duration.ofMinutes(processStartAccumulator)) }
        }
    }

    @Test
    fun `distribution based activity duration is respected`() {
        val tracesCount = 10
        val activityDurations = arrayListOf(1.0, 10.0, 2.0, 4.0, 4.0, 2.0, 1.0, 7.0, 11.0, 3.0)
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.now()
        val simulation = mockk<Simulation> { every { generateTraces() } returns (1..tracesCount).map { listOf(ActivityInstance(activity, null)) }.asSequence() }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returnsMany activityDurations }
        val roles = setOf("role")
        val resource = BasicResource(roles).apply { mockkObject(this) }
        val resourceBasedScheduler = ResourceBasedScheduler(
            simulation,
            listOf(resource),
            mapOf(activity.name to roles),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        resourceBasedScheduler.scheduleWith().map {
            it.values.minByOrNull { (start, stop) -> start }
        }.take(tracesCount).toList()

        (0 until tracesCount).forEach { traceIndex ->
            verify(atLeast = 1) { resource.enqueueActivity(Duration.ofMinutes(activityDurations[traceIndex].toLong()), any()) }
        }
    }

    @Test
    fun `throws if cannot find a resource with required role`() {
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val simulationStartOffset = Instant.now()
        val simulation = mockk<Simulation> { every { generateTraces() } returns listOf(listOf(ActivityInstance(activity, null))).asSequence() }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 1.0 }

        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val resource = BasicResource(setOf("role1")).apply { mockkObject(this) }
        val resourceBasedScheduler = ResourceBasedScheduler(
            simulation,
            listOf(resource),
            mapOf(activity.name to setOf("role2")),
            mapOf(activity.name to activityDurationDistribution),
            processInstanceOccurringRate,
            simulationStartOffset
        )

        assertThrows<NoSuchElementException> { resourceBasedScheduler.scheduleWith().take(1).toList() }
    }

    @Test
    fun `adding critical resources shortens the total execution time`() {
        val tracesCount = 100
        val simulationStartOffset = Instant.now()
        val activity = mockk<Activity> { every { name } returns "activity1" }
        val criticalActivity = mockk<Activity> { every { name } returns "activity2" }
        val simulation = mockk<Simulation> { every { generateTraces() } returns (1..tracesCount).map {
            val criticalActivityInstance = ActivityInstance(criticalActivity, null)
            listOf(criticalActivityInstance, ActivityInstance(activity, criticalActivityInstance))
        }.asSequence() }
        val processInstanceOccurringRate = mockk<RealDistribution> { every { sample() } returns 0.0 }
        val activityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 1.0 }
        val criticalActivityDurationDistribution = mockk<RealDistribution> { every { sample() } returns 3.0 }
        val roles = setOf("role")
        val criticalRoles = setOf("critical-role")
        val activitiesRoles = mapOf(activity.name to roles, criticalActivity.name to criticalRoles)
        val activitiesDurations = mapOf(activity.name to activityDurationDistribution, criticalActivity.name to criticalActivityDurationDistribution)
        val schedulerWithCriticalResource = ResourceBasedScheduler(
            simulation,
            listOf(BasicResource(roles), BasicResource(criticalRoles), BasicResource(criticalRoles), BasicResource(criticalRoles)),
            activitiesRoles,
            activitiesDurations,
            processInstanceOccurringRate,
            simulationStartOffset)
        val schedulerWithNoCriticalResource = ResourceBasedScheduler(
            simulation,
            listOf(BasicResource(roles), BasicResource(criticalRoles)),
            activitiesRoles,
            activitiesDurations,
            processInstanceOccurringRate,
            simulationStartOffset)

        val endTimeOfSimulationWithCriticalResource = schedulerWithCriticalResource.scheduleWith().map {
            it.values.maxOf { (start, stop) -> stop }
        }.take(tracesCount).last()
        val endTimeOfSimulationWithNoCriticalResource = schedulerWithNoCriticalResource.scheduleWith().map {
            it.values.maxOf { (start, stop) -> stop }
        }.take(tracesCount).last()

        val simulationWithCriticalResourceDuration = Duration.between(simulationStartOffset, endTimeOfSimulationWithCriticalResource)
        val simulationWithNoCriticalResourceDuration = Duration.between(simulationStartOffset, endTimeOfSimulationWithNoCriticalResource)

        assertTrue(simulationWithNoCriticalResourceDuration.toMinutes() / simulationWithCriticalResourceDuration.toMinutes() >= 2 )
    }
}