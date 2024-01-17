package processm.core.models.processtree

/**
 * Process tress for tests.
 */
object ProcessTrees {
    /**
     * The process tree without nodes.
     */
    val empty = ProcessTree()

    /**
     * A process tree from Fig. 7.27 in the Process Mining: Data Science in Action book.
     */
    val fig727 = ProcessTree.parse("→(A,⟲(→(∧(×(B,C),D),E),F),×(G,H))")

    /**
     * A process tree from Fig. 7.29 in the Process Mining: Data Science in Action book.
     */
    val fig729 = ProcessTree.parse("→(×(→(A,∧(C,E)),→(B,∧(D,F))),G)")

    /**
     * A process tree implementing a flower model of the activities A-Z, in which the empty trace is valid.
     */
    val azFlower = ProcessTree.parse("⟲(τ,A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z)")

    /**
     * A process tree made of two parallel flower trees: the first made of activities A,C,E,G,I,K,M,O,Q,S,U,W,Y,
     * the second made of activities B,D,F,H,J,L,N,P,R,T,V,X,Z.
     */
    val parallelFlowers = ProcessTree.parse("∧(⟲(τ,A,C,E,G,I,K,M,O,Q,S,U,W,Y),⟲(τ,B,D,F,H,J,L,N,P,R,T,V,X,Z))")

    val parallelDecisionsInLoop = ProcessTree.parse("⟲(∧(×(A,C,E,G,I,K,M,O,Q,S,U,W,Y),×(B,D,F,H,J,L,N,P,R,T,V,X,Z)),τ)")

    /**
     * A process tree implementing a sequence of two "a" activities.
     */
    val duplicateA = ProcessTree.parse("→(a,a)")

    /**
     * A process tree for the Journal Review log implementing only the mainstream path without the possibility to invite additional reviewers
     */
    val journalReviewMainstreamProcessTree = ProcessTree.parse(
        """
            →(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ×(accept, reject))
        """.trimIndent()
    )

    /**
     * A process tree for the Journal Review log implementing the complete process, incl. the possibility to invite additional reviewers
     */
    val journalReviewPerfectProcessTree = ProcessTree.parse(
        """
            →(invite reviewers, ∧(×(get review 1, time-out 1), ×(get review 2, time-out 2), ×(get review 3, time-out 3)), collect reviews, decide, ⟲(τ, →(invite additional reviewer, ×(get review X, time-out X))), ×(accept, reject))
        """.trimIndent()
    )

    /**
     * A process tree allowing for traces `a b c` and `b a c`
     */
    val twoParallelTasksAndSingleFollower = ProcessTree.parse("→(∧(a,b), c)")

    /**
     * A process tree with a repeated activity in the redo part of a loop, allowing for traces: `a b c a ...`, `a c b a ...`, and `a b a ...`
     */
    val loopWithRepeatedActivityInRedo = ProcessTree.parse("⟲(a, ∧(b,c), b)")

    /**
     * A process tree with an optional repetition of an activity in the redo part, allowing for traces: `a b c a ...`, `a b b c a ...` and `a b c b a ...`
     */
    val loopWithPossibilityOfRepeatedActivity = ProcessTree.parse("⟲(a, →(b, ×(c, ∧(b,c))))")

    /**
     * The same process tree as in [loopWithPossibilityOfRepeatedActivity], but the repetition is called `d` instead of `b`,
     * allowing for traces: `a b c a ...`, `a b d c a ...` and `a b c d a ...`
     */
    val loopWithSequenceAndExclusiveInRedo = ProcessTree.parse("⟲(a, →(b, ×(c, ∧(d,c))))")
}
