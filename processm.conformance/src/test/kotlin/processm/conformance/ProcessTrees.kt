package processm.conformance

import processm.core.models.processtree.ProcessTree

/**
 * Process tress for tests.
 */
object ProcessTrees {
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
}
