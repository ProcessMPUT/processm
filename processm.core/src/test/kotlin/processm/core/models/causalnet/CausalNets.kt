package processm.core.models.causalnet

/**
 * Causal nets for tests.
 */
object CausalNets {
    /**
     * The Causal net with only two activities: start and end.
     */
    val empty = causalnet { }

    /**
     * A Causal net based on Fig. 3.12 from Process Mining: Data Science in Action book.
     */
    val fig312: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        val f = Node("f")
        val g = Node("g")
        val h = Node("h")
        val z = Node("z")
        causalnet {
            start = a
            end = z
            a splits b + d
            a splits c + d

            a joins b
            f joins b
            b splits e

            a joins c
            f joins c
            c splits e

            a joins d
            f joins d
            d splits e

            b + d join e
            c + d join e
            e splits g
            e splits h
            e splits f

            e joins f
            f splits b + d
            f splits c + d

            e joins g
            g splits z

            e joins h
            h splits z

            g joins z
            h joins z
        }
    }

    /**
     * A Causal net based on Fig. 3.16 from Process Mining: Data Science in Action book.
     */
    val fig316: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val a = Node("a")
        val b = Node("b")
        val c = Node("c")
        val d = Node("d")
        val e = Node("e")
        causalnet {
            start = a
            end = e
            a splits b

            a joins b
            b joins b
            b splits c + d
            b splits b + c

            b joins c
            c splits d

            b + c join d
            c + d join d
            d splits d
            d splits e

            d joins e
        }
    }

    /**
     * A Causal net implementing a flower model of activities A-Z.
     */
    val azFlower: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val activities = "ABCDEFGHIJKLMNOPQRSTUVWXYZ".map { Node(it.toString()) }
        val tau = Node("τ", isSilent = true)
        causalnet {
            start = activities.first()
            end = activities.last()

            start splits tau
            start joins tau

            tau splits end
            tau joins end

            for (activity in activities.subList(1, activities.size - 1)) {
                tau splits activity
                tau joins activity
                activity splits tau
                activity joins tau
            }
        }
    }

    /**
     * A Causal net made of two flower models, the first containing activities A-M, the second activities N-Z.
     */
    val parallelDecisionsInLoop: CausalNet by lazy(LazyThreadSafetyMode.PUBLICATION) {
        val activities1 = "ABCDEFGHIJKLM".map { Node(it.toString()) }
        val activities2 = "NOPQRSTUVWXYZ".map { Node(it.toString()) }

        val st = Node("start", isSilent = true)
        val en = Node("end", isSilent = true)

        val loopStart = Node("ls")
        val loopEnd = Node("le")

        val dec1 = Node("d1")
        val dec2 = Node("d2")

        causalnet {
            start = st
            end = en

            st splits loopStart
            st joins loopStart
            loopStart splits dec1 + dec2

            loopStart joins dec1
            for (act1 in activities1) {
                dec1 splits act1
                dec1 joins act1
                act1 splits loopEnd
                for (act2 in activities2) {
                    act1 + act2 join loopEnd
                }
            }

            loopStart joins dec2
            for (act2 in activities2) {
                dec2 splits act2
                dec2 joins act2
                act2 splits loopEnd
            }

            loopEnd splits loopStart
            loopEnd joins loopStart

            loopEnd splits en
            loopEnd joins en
        }
    }

    /**
     * A sequential Causal net with two "a" activities run in line.
     */
    val duplicateA: CausalNet by lazy {
        val cnet = MutableCausalNet()

        val st = cnet.start
        val en = cnet.end
        val a1 = Node("a", instanceId = "1")
        val a2 = Node("a", instanceId = "2")

        cnet.addInstance(a1)
        cnet.addInstance(a2)

        val sta1 = cnet.addDependency(st, a1)
        val a1a2 = cnet.addDependency(a1, a2)
        val a2en = cnet.addDependency(a2, en)
        cnet.addSplit(Split(setOf(sta1)))
        cnet.addSplit(Split(setOf(a1a2)))
        cnet.addSplit(Split(setOf(a2en)))
        cnet.addJoin(Join(setOf(sta1)))
        cnet.addJoin(Join(setOf(a1a2)))
        cnet.addJoin(Join(setOf(a2en)))

        cnet
    }

    val perfectJournal: CausalNet by lazy {
        val inviteReviewers = Node("invite reviewers")
        val _beforeReview1 = Node("_before review 1", isSilent = true)
        val _beforeReview2 = Node("_before review 2", isSilent = true)
        val _beforeReview3 = Node("_before review 3", isSilent = true)
        val getReview1 = Node("get review 1")
        val getReview2 = Node("get review 2")
        val getReview3 = Node("get review 3")
        val getReviewX = Node("get review X")
        val timeOut1 = Node("time-out 1")
        val timeOut2 = Node("time-out 2")
        val timeOut3 = Node("time-out 3")
        val timeOutX = Node("time-out X")
        val _afterReview1 = Node("_after review 1", isSilent = true)
        val _afterReview2 = Node("_after review 2", isSilent = true)
        val _afterReview3 = Node("_after review 3", isSilent = true)
        val collect = Node("collect reviews")
        val decide = Node("decide")
        val _afterDecide = Node("_after decide", isSilent = true)
        val inviteAdditionalReviewer = Node("invite additional reviewer")
        val accept = Node("accept")
        val reject = Node("reject")
        val _end = Node("_end", isSilent = true)
        causalnet {
            start = inviteReviewers
            end = _end

            inviteReviewers splits _beforeReview1 + _beforeReview2 + _beforeReview3

            inviteReviewers joins _beforeReview1
            inviteReviewers joins _beforeReview2
            inviteReviewers joins _beforeReview3
            _beforeReview1 splits getReview1 or timeOut1
            _beforeReview2 splits getReview2 or timeOut2
            _beforeReview3 splits getReview3 or timeOut3

            _beforeReview1 joins getReview1
            _beforeReview1 joins timeOut1
            _beforeReview2 joins getReview2
            _beforeReview2 joins timeOut2
            _beforeReview3 joins getReview3
            _beforeReview3 joins timeOut3

            getReview1 splits _afterReview1
            timeOut1 splits _afterReview1
            getReview2 splits _afterReview2
            timeOut2 splits _afterReview2
            getReview3 splits _afterReview3
            timeOut3 splits _afterReview3
            getReview1 or timeOut1 join _afterReview1
            getReview2 or timeOut2 join _afterReview2
            getReview3 or timeOut3 join _afterReview3

            _afterReview1 splits collect
            _afterReview2 splits collect
            _afterReview3 splits collect
            _afterReview1 + _afterReview2 + _afterReview3 join collect

            collect splits decide
            collect joins decide

            decide splits _afterDecide
            decide or getReviewX or timeOutX join _afterDecide

            _afterDecide splits inviteAdditionalReviewer or accept or reject
            _afterDecide joins inviteAdditionalReviewer
            _afterDecide joins accept
            _afterDecide joins reject

            inviteAdditionalReviewer splits getReviewX or timeOutX
            inviteAdditionalReviewer joins getReviewX
            inviteAdditionalReviewer joins timeOutX
            getReviewX splits _afterDecide
            timeOutX splits _afterDecide

            accept splits _end
            accept joins _end
            reject splits _end
            reject joins _end
        }
    }
}
