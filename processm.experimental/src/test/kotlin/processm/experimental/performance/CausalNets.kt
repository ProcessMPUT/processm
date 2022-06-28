package processm.experimental.performance

import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet

object CausalNets {
    val a = Node("a")
    val b = Node("b")
    val c = Node("c")
    val d = Node("d")
    val e = Node("e")
    val f = Node("f")
    val g = Node("g")
    val h = Node("h")

    val model0 = causalnet {
        start = a
        end = e
        a splits b
        b splits c + d
        c splits e
        d splits e
        a joins b
        b joins c
        b joins d
        c + d join e
    }


    /**
     * From "Replaying History on Process Models for Conformance Checking and Performance Analysis"
     */
    val model1 = causalnet {
        start splits a
        a splits (b + d) or (c + d)
        b splits e
        c splits e
        d splits e
        e splits g or h or f
        g splits end
        h splits end
        f splits (b + d) or (c + d)
        start joins a
        a or f join b
        a or f join c
        a or f join d
        b + d or c + d join e
        e joins g
        e joins h
        e joins f
        g joins end
        h joins end
    }

    val model2 = causalnet {
        start splits a
        a splits c
        c splits d
        d splits e
        e splits h
        h splits end
        start joins a
        a joins c
        c joins d
        d joins e
        e joins h
        h joins end
    }

    val model3: CausalNet = causalnet {
        start splits a
        a splits b or c or d or e or f
        b splits c or d or e or f or g or h
        c splits b or d or e or f or g or h
        d splits b or c or e or f or g or h
        e splits b or c or d or f or g or h
        f splits b or c or d or e or g or h
        g splits end
        h splits end
        start joins a
        a or c or d or e or f join b
        a or b or d or e or f join c
        a or b or c or e or f join d
        a or b or c or d or f join e
        a or b or c or d or e join f
        f or b or c or d or e join g
        f or b or c or d or e join h
        g joins end
        h joins end
    }

    val model5 = causalnet {
        start = a
        end = e
        a splits b or c or b + c
        b splits d
        c splits d
        d splits e
        a joins b
        a joins c
        b or c or b + c join d
        d joins e
    }

    val model6 = causalnet {
        start splits a
        start joins a
        a splits b
        b splits c + d
        c splits e
        d splits e
        a joins b
        b joins c
        b joins d
        c + d join e
        e splits end
        e joins end
    }

    val model7 = causalnet {
        start = a
        end = e
        a splits b or c
        b splits e
        c splits d
        d splits e
        a joins b
        a joins c
        c joins d
        b or d join e
    }
}
