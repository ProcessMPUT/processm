package processm.core.models.causalnet

import io.mockk.every
import io.mockk.mockk
import processm.core.log.AttributeMap
import processm.core.log.Event
import processm.core.log.MutableAttributeMap
import processm.core.log.hierarchical.Trace
import kotlin.test.Test

class PoCDecisionModelTest {

    val featureName = "feature"

    private fun event(name: String, feature: Long): Event {
        val e = mockk<Event>()
        every { e.conceptName } returns name
        every { e.lifecycleTransition } returns null
        every { e.attributes } returns MutableAttributeMap().also {
            it[featureName] = feature
        }
        return e
    }

    private fun trace(vararg nodes: Node, feature: Long): Trace =
        Trace(nodes.asList().map { event(it.name, feature) }.asSequence())

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")

    @Test
    fun test() {
        val model = causalnet {
            start = a
            end = e
            a splits b or d
            b splits c
            c splits e
            d splits e
            a joins b
            b joins c
            a joins d
            c or d join e
        }
        val decisionModel = PoCDecisionModel(featureName)
        val replayer = BasicReplayer(model)
        val tbc1 = trace(a, b, c, e, feature = 1L)
        val td1 = trace(a, d, e, feature = 1L)
        val td2 = trace(a, d, e, feature = 2L)
        decisionModel.fit(
            sequenceOf(
                Row(tbc1, replayer.replay(tbc1).single()),
                Row(tbc1, replayer.replay(tbc1).single()),
                Row(tbc1, replayer.replay(tbc1).single()),
                Row(td1, replayer.replay(td1).single()),
                Row(td2, replayer.replay(td2).single()),
            )
        )
        decisionModel.predict(Row(tbc1, replayer.replay(tbc1).single())).forEach { println(it) }
        println()
    }
}