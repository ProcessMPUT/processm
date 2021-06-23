package processm.experimental.onlinehmpaper

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import java.io.FileOutputStream
import kotlin.test.Test

class PM4PyExporterTest {

    @Test
    fun test() {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val net = causalnet {
            start splits a
            a splits b or c or b + c
            b splits d
            c splits d
            d splits end
            start joins a
            a joins b
            a joins c
            b or c or b + c join d
            d joins end
        }
        FileOutputStream("/tmp/test.xml").use {
            net.toPM4PY(it)
        }
    }
}