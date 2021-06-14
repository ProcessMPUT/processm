package processm.experimental.heuristicminer.windowing

import ch.qos.logback.classic.Level
import processm.core.log.Helpers.logFromString
import processm.core.log.hierarchical.Log
import processm.core.logging.logger
import processm.experimental.heuristicminer.Helper
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertTrue

class EfficiencyTests {

    @Test
    fun test() {
        val log = logFromString("m w o m w o w m o")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.01 }
    }

    @Test
    fun test2() {
        val log = logFromString("a b b a c a c")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.01 }
    }

    @Test
    fun test3() {
        val log =
            logFromString("a b c d e f g b a h i a h i a b j h a b k")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.01 }
    }

    @Test
    fun test4() {
        val log =
            logFromString("a b a b a c d c d")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.01 }
    }

    @Test
    fun test5() {
        val log =
            logFromString("a b c d e f g h i c e e c e c j")
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        hm.processLog(log)
        println(hm.result)
        assertTrue { (hm.replayer as SingleReplayer).efficiency <= 1.01 }
    }

    @Test
    fun diamond() {
        val log = logFromString(
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
        val log = logFromString(
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
        assertTrue { eff.all { it <= 1.01 } }
    }

    private fun randomStrings(n: Int, k: Int, a: Int, rnd: Random = Random(42)): List<Double> {
        val events = (0 until a).map { ('a'.toInt() + it).toChar() }
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val hm = WindowingHeuristicMiner()
        for (i in 0 until n) {
            val log = logFromString(List(k) { events[rnd.nextInt(events.size)] }.joinToString(separator = " "))
            hm.processDiff(log, Log(emptySequence()))
        }
        return (hm.replayer as SingleReplayer).groupEfficiency
    }

    @Test
    fun `random 1 10`() {
        val eff = randomStrings(1, 10, 3).single()
        assertTrue { eff <= 1.01 }
    }

    @Test
    fun `random 1 20`() {
        val eff = randomStrings(1, 20, 3).single()
        assertTrue { eff <= 1.01 }
    }

    @Test
    fun `random 1 40`() {
        val eff = randomStrings(1, 40, 3).single()
        assertTrue { eff <= 1.01 }
    }

    @Test
    fun `random 1 100`() {
        val eff = randomStrings(1, 100, 3).single()
        assertTrue { eff <= 1.01 }
    }

    @Test
    fun `random 1 15 3 22`() {
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val log = logFromString("c c c c c c a b b c a b c c b")
        hm.processLog(log)
        println(hm.result)
        val eff = (hm.replayer as SingleReplayer).efficiency
        assertTrue { eff <= 1.61 }
    }

    @Test
    fun t3() {
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val log = logFromString("c c b b c a b c c b")
        hm.processLog(log)
        println(hm.result)
        val eff = (hm.replayer as SingleReplayer).efficiency
        assertTrue { eff <= 1.35 }
    }

    @Test
    fun t4() {
        val hm = WindowingHeuristicMiner()
        (SingleReplayer.logger() as ch.qos.logback.classic.Logger).level = Level.TRACE
        val log = logFromString("a b e c c a c b f c f")
        hm.processLog(log)
        println(hm.result)
        val eff = (hm.replayer as SingleReplayer).efficiency
        assertTrue { eff <= 1.01 }
    }

}