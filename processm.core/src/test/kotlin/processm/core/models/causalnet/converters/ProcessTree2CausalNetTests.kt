package processm.core.models.causalnet.converters

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.processtree.ProcessTrees
import kotlin.test.Test
import kotlin.test.assertTrue

class ProcessTree2CausalNetTests {
    /**
     * Test for conversion of proces tree from Fig. 7.27 in the Process Mining: Data Science in Action book.
     */
    @Test
    fun fig7_27() {
        val translated = ProcessTrees.fig727.toCausalNet()
        val reference = causalnet {
            /*
                         /-o---o- F <-o----------o
                         v                        \
            A -o---o-> tau1 -oo---o-> B -o---oo-> E --o---o-> G -o---o-> tau2
                         | | ||              ||   ^^\-o---o-> H -o-------o^
                         | \-|o---o-> C -o---o|---/|
                         |   |                |    |
                         \---o----o-> D -o----o----/
            */
            val A = Node("A")
            val tau1 = Node("→∧", "2", isSilent = true)
            val B = Node("B")
            val C = Node("C")
            val D = Node("D")
            val E = Node("E")
            val F = Node("F")
            val G = Node("G")
            val H = Node("H")
            val tau2 = Node("×→", "4", isSilent = true)

            start = A
            end = tau2

            A splits tau1

            A or F join tau1
            tau1 splits B + D or C + D

            tau1 joins B
            B splits E

            tau1 joins C
            C splits E

            tau1 joins D
            D splits E

            B + D or C + D join E
            E splits F or G or H

            E joins F
            F splits tau1

            E joins G
            G splits tau2

            E joins H
            H splits tau2

            G or H join tau2
        }

        assertTrue(reference.structurallyEquals(translated))
    }
}
