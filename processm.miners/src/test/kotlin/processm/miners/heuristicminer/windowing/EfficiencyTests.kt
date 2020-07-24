package processm.miners.heuristicminer.windowing

import ch.qos.logback.classic.Level
import processm.core.log.hierarchical.Log
import kotlin.test.*
import processm.core.logging.logger
import processm.miners.heuristicminer.Helper

class EfficiencyTests {

    @Test
    fun test() {
        val log = Helper.logFromString("m w o m w o w m o")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.15 }
    }

    @Test
    fun test2() {
        val log = Helper.logFromString("a b b a c a c")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.06 }
    }

    @Test
    fun test3() {
        val log=
            Helper.logFromString("a b c d e f g b a h i a h i a b j h a b k")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 15.05 }
    }

    @Test
    fun test4() {
        val log=
            Helper.logFromString("a b a b a c d c d")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.05 }
    }

    @Test
    fun test5() {
        val log=
            Helper.logFromString("a b c d e f g h i c e e c e c j")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm=WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.35 }
    }

    @Test
    fun diamond() {
        val log = Helper.logFromString(
            """
                a b c d
                a c b d 
            """.trimIndent()
        )
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).groupEfficiency.all { it <= 1.001 } }
    }

    @Test
    fun `loops prove that a run-all-consume-all strategy is not enough`() {
        val log = Helper.logFromString(
            """
            a b c
            a b b c
            a b b b c
        """.trimIndent()
        )
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        hm.processDiff(log, Log(emptySequence()))
        println(hm.result)
        val eff = (hm.replayer as SingleReplayer).groupEfficiency
        assertTrue { eff[0] <= 1.01 }
        assertTrue { eff[0] <= 1.01 }
        assertTrue { eff[0] <= 1.08 }
    }
}