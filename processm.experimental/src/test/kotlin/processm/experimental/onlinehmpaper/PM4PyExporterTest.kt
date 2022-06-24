package processm.experimental.onlinehmpaper

import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import java.io.File
import java.io.FileOutputStream
import kotlin.test.Ignore
import kotlin.test.Test

class PM4PyExporterTest {

    @Test
    @Ignore("This test creates data for the manual verification.")
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
        FileOutputStream(File.createTempFile("test", ".xml")).use {
            net.toPM4PY(it)
        }
    }
}
