package processm.miners.heuristicminer

import processm.miners.heuristicminer.dummy.Log


internal data class Edge(val a: Int, val b: Int, val longdistance: Boolean = false)

class HeuristicMiner(baselog: Log) {

    val log = SimplifiedLog(baselog)

    init {
        DependencyGraph(log)
    }


//    fun computeDependencyGraph(
//        sigma_l1l: Double = .9,
//        sigma_l2l: Double = .9,
//        sigma_a: Double = .9,
//        sigma_r: Double = .05
//    ) {
//        // length-1 loops
//
//        println(c1.toList())
//        // length-2 loops
//
//        println(c2.toList())
//        //the strongest follower
//
//        println(strongestFollowers.toList())
//        //filter out the weak outgoing-connections for length-two loop
//
//        println("Cout: " + cout.toList())
//        //the strongest cause
//
//        println(cin.toList())
//    }

}