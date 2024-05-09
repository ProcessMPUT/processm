package processm.miners.causalnet.onlineminer.replayer

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.MutableCausalNet
import processm.core.models.causalnet.Node
import kotlin.test.assertEquals

class SingleReplayerTest {

    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
    private val model: CausalNet

    init {
        model = MutableCausalNet(start = a, end = e)
        model.addInstance(a, b, c, d, e)
        for (x in listOf(a, b, c, d, e))
            for (y in listOf(b, c, d, e))
                model.addDependency(x, y)
    }

    @ParameterizedTest
    @ValueSource(ints = [1, 2, 3, 4])
    fun `max binding size is equal to the horizon size`(horizon: Int) {
        val (splits, joins) = SingleReplayer(horizon = horizon).replay(model, listOf(a, b, c, d, e))
        assertEquals(horizon, splits.maxOf { it.size })
        assertEquals(horizon, joins.maxOf { it.size })
    }
}