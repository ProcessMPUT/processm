package processm.enhancement.metadata

import processm.conformance.alignment
import processm.core.DBTestHelper
import processm.core.helpers.mapToSet
import processm.core.log.InferConceptInstanceFromStandardLifecycle
import processm.core.log.hierarchical.DBHierarchicalXESInputStream
import processm.core.log.hierarchical.toFlatSequence
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.*
import processm.core.models.petrinet.PetriNet
import processm.core.models.petrinet.converters.toPetriNet
import processm.core.models.processtree.ProcessTree
import processm.core.querylanguage.Query
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ModelTimesMetadataTest {

    companion object {
        val journalCNet = causalnet {
            start = Node("start", "", true)
            end = Node("end", "", true)
            Node("decide") or Node("get review X") + Node("decide") or Node("get review X") + Node("time-out X") + Node(
                "decide"
            ) join Node("accept")
            Node("accept") splits end
            Node("get review 1") + Node("get review 2") + Node("get review 3") or Node("time-out 1") + Node("time-out 3") + Node(
                "time-out 2"
            ) or Node("get review 1") + Node("time-out 2") + Node("get review 3") or Node("get review 1") + Node("time-out 3") + Node(
                "get review 2"
            ) or Node("time-out 1") + Node("get review 2") + Node("get review 3") or Node("time-out 1") + Node("time-out 2") + Node(
                "get review 3"
            ) or Node("time-out 1") + Node("time-out 3") + Node("get review 2") or Node("get review 1") + Node("time-out 3") + Node(
                "time-out 2"
            ) join Node("collect reviews")
            Node("collect reviews") splits Node("decide")
            Node("collect reviews") joins Node("decide")
            Node("decide") splits Node("accept") + Node("invite additional reviewer") or Node("accept") or Node("invite additional reviewer") + Node(
                "reject"
            ) or Node("reject")
            Node("accept") + Node("invite reviewers") or Node("invite reviewers") or Node("reject") + Node("invite reviewers") join end
            Node("time-out 3") + Node("invite reviewers") or Node("invite reviewers") + Node("get review 3") or Node("invite reviewers") + Node(
                "get review 2"
            ) or Node("time-out 3") + Node("invite reviewers") + Node("get review 2") or Node("time-out 2") + Node("invite reviewers") + Node(
                "get review 3"
            ) or Node("invite reviewers") or Node("invite reviewers") + Node("get review 2") + Node("get review 3") or Node(
                "time-out 2"
            ) + Node("invite reviewers") join Node("get review 1")
            Node("get review 1") splits Node("collect reviews") + Node("time-out 3") or Node("collect reviews") + Node("time-out 2") + Node(
                "get review 3"
            ) or Node("collect reviews") + Node("time-out 3") + Node("get review 2") or Node("collect reviews") + Node("get review 2") + Node(
                "get review 3"
            ) or Node("collect reviews") + Node("time-out 3") + Node("time-out 2") or Node("collect reviews") + Node("get review 3") or Node(
                "collect reviews"
            ) + Node("get review 2") or Node("collect reviews") or Node("collect reviews") + Node("time-out 2")
            Node("invite reviewers") + Node("get review 3") or Node("time-out 3") + Node("invite reviewers") or Node("get review 1") + Node(
                "invite reviewers"
            ) + Node("get review 3") or Node("invite reviewers") or Node("time-out 1") + Node("invite reviewers") or Node(
                "get review 1"
            ) + Node("time-out 3") + Node("invite reviewers") or Node("time-out 1") + Node("invite reviewers") + Node("get review 3") or Node(
                "get review 1"
            ) + Node("invite reviewers") join Node("get review 2")
            Node("get review 2") splits Node("collect reviews") + Node("time-out 3") or Node("collect reviews") + Node("get review 1") or Node(
                "collect reviews"
            ) + Node("get review 3") or Node("collect reviews") + Node("get review 1") + Node("get review 3") or Node("collect reviews") or Node(
                "collect reviews"
            ) + Node("get review 1") + Node("time-out 3") or Node("time-out 1") + Node("collect reviews") + Node("get review 3") or Node(
                "time-out 1"
            ) + Node("collect reviews") + Node("time-out 3") or Node("collect reviews") + Node("time-out 1")
            Node("get review 1") + Node("invite reviewers") + Node("get review 2") or Node("time-out 1") + Node("time-out 2") + Node(
                "invite reviewers"
            ) or Node("time-out 1") + Node("invite reviewers") or Node("invite reviewers") or Node("time-out 2") + Node(
                "invite reviewers"
            ) or Node("get review 1") + Node("invite reviewers") or Node("invite reviewers") + Node("get review 2") or Node(
                "get review 1"
            ) + Node("time-out 2") + Node("invite reviewers") or Node("time-out 1") + Node("invite reviewers") + Node("get review 2") join Node(
                "get review 3"
            )
            Node("get review 3") splits Node("collect reviews") + Node("get review 1") or Node("collect reviews") + Node(
                "get review 2"
            ) or Node("collect reviews") + Node("get review 1") + Node("time-out 2") or Node("collect reviews") + Node("time-out 1") + Node(
                "get review 2"
            ) or Node("collect reviews") + Node("time-out 2") or Node("collect reviews") + Node("get review 1") + Node("get review 2") or Node(
                "collect reviews"
            ) or Node("collect reviews") + Node("time-out 1") or Node("collect reviews") + Node("time-out 1") + Node("time-out 2")
            Node("invite additional reviewer") joins Node("get review X")
            Node("get review X") splits Node("invite additional reviewer") or Node("accept") + Node("invite additional reviewer") or Node(
                "reject"
            ) or Node("accept") or Node("invite additional reviewer") + Node("reject")
            Node("get review X") or Node("decide") or Node("time-out X") join Node("invite additional reviewer")
            Node("invite additional reviewer") splits Node("time-out X") or Node("get review X")
            start joins Node("invite reviewers")
            Node("invite reviewers") splits Node("time-out 1") + Node("time-out 3") + Node("time-out 2") + end or Node("time-out 1") + Node(
                "time-out 2"
            ) + end + Node("get review 3") or Node("time-out 1") + Node("time-out 3") + end + Node("get review 2") or Node(
                "get review 1"
            ) + Node("time-out 3") + Node("time-out 2") + end or Node("get review 1") + Node("time-out 2") + end + Node(
                "get review 3"
            ) or Node("get review 1") + Node("time-out 3") + end + Node("get review 2") or Node("time-out 1") + end + Node(
                "get review 2"
            ) + Node("get review 3") or end or Node("get review 1") + end + Node("get review 2") + Node("get review 3")
            Node("get review X") + Node("time-out X") + Node("decide") or Node("decide") or Node("get review X") + Node(
                "decide"
            ) join Node("reject")
            Node("reject") splits end
            start splits Node("invite reviewers")
            Node("time-out 2") + Node("invite reviewers") or Node("time-out 3") + Node("invite reviewers") or Node("time-out 3") + Node(
                "time-out 2"
            ) + Node("invite reviewers") or Node("invite reviewers") or Node("time-out 2") + Node("invite reviewers") + Node(
                "get review 3"
            ) or Node("time-out 3") + Node("invite reviewers") + Node("get review 2") or Node("invite reviewers") + Node(
                "get review 3"
            ) or Node("invite reviewers") + Node("get review 2") or Node("invite reviewers") + Node("get review 2") + Node(
                "get review 3"
            ) join Node("time-out 1")
            Node("time-out 1") splits Node("collect reviews") + Node("time-out 2") or Node("collect reviews") + Node("get review 2") or Node(
                "collect reviews"
            ) + Node("get review 2") + Node("get review 3") or Node("collect reviews") + Node("time-out 2") + Node("get review 3") or Node(
                "collect reviews"
            ) + Node("time-out 3") + Node("get review 2") or Node("collect reviews") + Node("get review 3") or Node("collect reviews") or Node(
                "collect reviews"
            ) + Node("time-out 3") + Node("time-out 2") or Node("collect reviews") + Node("time-out 3")
            Node("get review 1") + Node("invite reviewers") or Node("time-out 1") + Node("time-out 3") + Node("invite reviewers") or Node(
                "time-out 3"
            ) + Node("invite reviewers") or Node("time-out 1") + Node("invite reviewers") or Node("time-out 1") + Node("invite reviewers") + Node(
                "get review 3"
            ) or Node("get review 1") + Node("time-out 3") + Node("invite reviewers") or Node("get review 1") + Node("invite reviewers") + Node(
                "get review 3"
            ) or Node("invite reviewers") or Node("invite reviewers") + Node("get review 3") join Node("time-out 2")
            Node("time-out 2") splits Node("time-out 1") + Node("collect reviews") + Node("get review 3") or Node("collect reviews") + Node(
                "get review 1"
            ) + Node("time-out 3") or Node("time-out 1") + Node("collect reviews") + Node("time-out 3") or Node("collect reviews") + Node(
                "time-out 1"
            ) or Node("collect reviews") + Node("get review 3") or Node("collect reviews") + Node("time-out 3") or Node(
                "collect reviews"
            ) or Node("collect reviews") + Node("get review 1") + Node("get review 3") or Node("collect reviews") + Node(
                "get review 1"
            )
            Node("invite reviewers") + Node("get review 2") or Node("get review 1") + Node("time-out 2") + Node("invite reviewers") or Node(
                "time-out 1"
            ) + Node("invite reviewers") + Node("get review 2") or Node("time-out 2") + Node("invite reviewers") or Node(
                "get review 1"
            ) + Node("invite reviewers") + Node("get review 2") or Node("time-out 1") + Node("invite reviewers") or Node(
                "invite reviewers"
            ) or Node("time-out 1") + Node("time-out 2") + Node("invite reviewers") join Node("time-out 3")
            Node("time-out 3") splits Node("time-out 1") + Node("collect reviews") + Node("get review 2") or Node("collect reviews") + Node(
                "get review 1"
            ) + Node("time-out 2") or Node("collect reviews") + Node("time-out 2") or Node("collect reviews") + Node("time-out 1") or Node(
                "collect reviews"
            ) or Node("collect reviews") + Node("get review 1") + Node("get review 2") or Node("collect reviews") + Node(
                "get review 1"
            ) or Node("collect reviews") + Node("time-out 1") + Node("time-out 2")
            Node("invite additional reviewer") joins Node("time-out X")
            Node("time-out X") splits Node("accept") or Node("invite additional reviewer") + Node("reject") or Node("reject") or Node(
                "accept"
            ) + Node("invite additional reviewer") or Node("invite additional reviewer")
        }

        val journalProcessTree =
            ProcessTree.parse("→(⟲(invite reviewers,τ),∧(×(get review 2,time-out 2),×(get review 1,time-out 1),×(get review 3,time-out 3)),⟲(τ,collect reviews),⟲(τ,decide),⟲(τ,invite additional reviewer,get review X,time-out X),×(⟲(τ,accept),⟲(reject,τ),τ))")

        val journalPetriNet = journalCNet.toPetriNet()
    }

    private val journal by lazy {
        InferConceptInstanceFromStandardLifecycle(
            DBHierarchicalXESInputStream(DBTestHelper.dbName, Query("where l:id=${DBTestHelper.JournalReviewExtra}"))
                .toFlatSequence()
        )
    }

    @Test
    fun causalnet() {
        val model = MutableCausalNet()
        model.copyFrom(journalCNet) { it }
        model.extendWithTimesMetadataFromStream(journal)
        checkModel(model)
    }

    @Test
    fun `process tree`() {
        val handler = DefaultMutableMetadataHandler()
        val model = ProcessTree(journalProcessTree.root, handler)
        handler.extendWithTimesMetadataFromStream(model, journal)
        checkModel(model)
    }

    @Test
    fun `petri net`() {
        val handler = DefaultMutableMetadataHandler()
        val model = with(journalPetriNet) {
            PetriNet(places, transitions, initialMarking, finalMarking, handler)
        }
        handler.extendWithTimesMetadataFromStream(model, journal)
        checkModel(model)
    }

    /**
     * The numerical values are computed by the tested code itself and serve only to prevent regressions and to ensure uniform results across all considered types of process models
     */
    private fun <T> checkModel(model: T) where T : ProcessModel, T : MetadataHandler {
        operator fun T.get(activity: String, metadata: URN) =
            model.getMetadata(this.activities.single { it.name == activity }, metadata)
        assertTrue { model.availableMetadata.contains(BasicMetadata.WAITING_TIME) }
        assertTrue { model.availableMetadata.contains(BasicMetadata.LEAD_TIME) }
        assertTrue { model.availableMetadata.contains(BasicMetadata.SERVICE_TIME) }
        with(model["invite reviewers", BasicMetadata.WAITING_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ZERO, max)
            assertEquals(101, count)
        }
        with(model["invite reviewers", BasicMetadata.LEAD_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ofHours(288), max)
            assertEquals(64, average.toHours())
            assertEquals(101, count)
        }
        with(model["invite reviewers", BasicMetadata.SERVICE_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ofHours(288), max)
            assertEquals(64, average.toHours())
            assertEquals(101, count)
        }
        with(model["collect reviews", BasicMetadata.WAITING_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ZERO, max)
            assertEquals(100, count)
        }
        with(model["collect reviews", BasicMetadata.LEAD_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ofHours(120), max)
            assertEquals(50, average.toHours())
            assertEquals(100, count)
        }
        with(model["collect reviews", BasicMetadata.SERVICE_TIME]) {
            assertIs<DurationDistributionMetadata>(this)
            assertEquals(Duration.ofHours(120), max)
            assertEquals(50, average.toHours())
            assertEquals(100, count)
        }
    }

    @Test
    fun `correct attribution`() {
        val d1 = Duration.ofHours(1)
        val d2 = Duration.ofHours(2)
        val providers = sequenceOf(alignment {
            "a" with (BasicMetadata.WAITING_TIME.urn to d1.toString()) executing "a"
            "b" with (BasicMetadata.SERVICE_TIME.urn to d2.toString()) executing "b"
        }).getTimesMetadataProviders()
        assertEquals(2, providers.size)
        assertEquals(setOf(BasicMetadata.WAITING_TIME, BasicMetadata.SERVICE_TIME), providers.mapToSet { it.name })
        with(providers.single { it.name == BasicMetadata.WAITING_TIME }) {
            assertEquals(1, keys.size)
            with(keys.single()) {
                assertIs<Activity>(this)
                assertEquals("a", name)
            }
            with(values.single()) {
                assertIs<DurationDistributionMetadata>(this)
                assertEquals(d1, min)
                assertEquals(d1, max)
                assertEquals(1, count)
            }
        }
        with(providers.single { it.name == BasicMetadata.SERVICE_TIME }) {
            assertEquals(1, keys.size)
            with(keys.single()) {
                assertIs<Activity>(this)
                assertEquals("b", name)
            }
            with(values.single()) {
                assertIs<DurationDistributionMetadata>(this)
                assertEquals(d2, min)
                assertEquals(d2, max)
                assertEquals(1, count)
            }
        }
    }

    @Test
    fun `correct aggregation`() {
        val d1 = Duration.ofHours(1)
        val d2 = Duration.ofHours(2)
        val providers = sequenceOf(alignment {
            "a" with (BasicMetadata.WAITING_TIME.urn to d1.toString()) executing "a"
            "a" with (BasicMetadata.WAITING_TIME.urn to d2.toString()) executing "a"
        }).getTimesMetadataProviders()
        assertEquals(1, providers.size)
        assertEquals(setOf(BasicMetadata.WAITING_TIME), providers.mapToSet { it.name })
        with(providers.single()) {
            assertEquals(1, keys.size)
            with(keys.single()) {
                assertIs<Activity>(this)
                assertEquals("a", name)
            }
            with(values.single()) {
                assertIs<DurationDistributionMetadata>(this)
                assertEquals(d1, min)
                assertEquals(d2, max)
                assertEquals(2, count)
            }
        }
    }
}