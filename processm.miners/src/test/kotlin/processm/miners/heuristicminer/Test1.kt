package processm.miners.heuristicminer


import processm.miners.heuristicminer.dummy.Event
import processm.miners.heuristicminer.dummy.Log
import processm.miners.heuristicminer.dummy.Trace
import kotlin.test.*

class Test1 {
    init {

    }

    @Test
    fun test() {
        //['_start', 'a', 'b1', 'c', 'd1', 'e', '_end']
        var log = Log(
            listOf(
                Trace(listOf(Event("a"), Event("b1"), Event("c"), Event("d1"), Event("e"))),
                Trace(listOf(Event("a"), Event("b1"), Event("c"), Event("d1"), Event("e"))),
                Trace(listOf(Event("a"), Event("b2"), Event("c"), Event("d2"), Event("e")))
            )
        )
        println(log)
        var hm = HeuristicMiner(log)
    }
}