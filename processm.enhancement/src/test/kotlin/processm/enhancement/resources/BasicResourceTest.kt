package processm.enhancement.resources

import java.time.Duration
import java.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BasicResourceTest {
    lateinit var basicResource: BasicResource
    
    @BeforeTest
    fun setUp() {
        basicResource = BasicResource("basic", emptySet())
    }

    @Test
    fun `nearest availability is changed after enqueueing activities`() {
        val enqueuedActivityOffset = Instant.now()
        val enqueuedActivityDuration = Duration.ofMinutes(5)
        val availabilityBeforeEnqueueing = basicResource.getNearestAvailability(enqueuedActivityDuration, enqueuedActivityOffset)

        basicResource.enqueueActivity(enqueuedActivityDuration, enqueuedActivityOffset)

        val availabilityAfterEnqueueing = basicResource.getNearestAvailability(enqueuedActivityDuration, enqueuedActivityOffset)
        assertEquals(enqueuedActivityDuration, Duration.between(availabilityBeforeEnqueueing, availabilityAfterEnqueueing))
    }

    @Test
    fun `activity is enqueued between other activities if timeslot is wide enough`() {
        val enqueuedActivityOffset = Instant.now()
        val enqueuedActivitiesDuration = Duration.ofMinutes(5)
        basicResource.enqueueActivity(enqueuedActivitiesDuration, enqueuedActivityOffset)
        val activityToBeStartedInTheFuture = basicResource.enqueueActivity(enqueuedActivitiesDuration, enqueuedActivityOffset + Duration.ofMinutes(20))

        val activityToBeStartedNow = basicResource.enqueueActivity(enqueuedActivitiesDuration, enqueuedActivityOffset)

        assertTrue( activityToBeStartedInTheFuture > activityToBeStartedNow)
    }
}
