package processm.miners.heuristicminer.dependencygraphproviders

import processm.core.models.causalnet.Dependency

/**
 * Dependency graph provider following Definition 6 in A.J.M.M. Weijters and J.T.S. Ribeiro "Flexible Heuristics Miner (FHM)" DOI: 10.1109/CIDM.2011.5949453
 * The default values of the parameters also follow the paper.
 *
 * @see L2DependencyGraphProvider
 * @param minL1 Minimal dependency value for loops of length 1
 * @param sigma_r Margin of indistinguishability from the best dependencies ("Relative-to-best threshold")
 */
open class FHMDependencyGraphProvider(
    minDirectlyFollows: Int,
    minDependency: Double = 0.9,
    minL2: Double = 0.9,
    protected val minL1: Double = 0.9,
    protected val sigma_r: Double = 0.05
) : L2DependencyGraphProvider(minDirectlyFollows, minDependency, minL2) {


    override fun computeDependencyGraph(): List<Dependency> {
        val T = nodes
        val T2 = T.flatMap { a -> T.map { b -> Dependency(a, b) } }
        val C1 = T.filter { a -> dependency(a, a) >= minL1 }.map { a -> Dependency(a, a) }
        val C2 = T2
            .filterNot { (a, b) -> Dependency(a, a) in C1 || Dependency(b, b) in C1 }
            .filter { (a, b) -> dependency2(a, b) >= minL2 }
        val Cout = T2.filter { (a, b) ->
            a != end && b != start && a != b && T.all { y -> dependency(a, b) >= dependency(a, y) }
        }
            .toMutableSet()
        val Cpout = Cout.filter { (a, x) ->
            dependency(a, x) < minDependency &&
                    Cout.any { (b, y) -> Dependency(a, b) in C2 && dependency(y, b) - dependency(x, a) > sigma_r }
        }
        Cout.removeAll(Cpout)
        val Cin = T2
            .filter { (a, b) ->
                a != end && b != start && a != b &&
                        T.all { x -> dependency(a, b) >= dependency(x, b) }
            }
            .toMutableSet()
        val Cpin = Cin.filter { (x, a) ->
            dependency(x, a) < minDependency &&
                    Cin.any { (y, b) -> Dependency(a, b) in C2 && dependency(y, b) - dependency(x, a) > sigma_r }
        }
        Cin.removeAll(Cpin)
        val Cbout = T2.filter { (a, b) -> b != start }.filter { (a, b) ->
            dependency(a, b) >= minDependency ||
                    T.any { c -> Dependency(a, c) in Cout && b != c && dependency(a, c) - dependency(a, b) < sigma_r }
        }
        val Cbin = T2.filter { (a, b) -> a != end }.filter { (b, a) ->
            dependency(b, a) >= minDependency ||
                    T.any { c -> Dependency(b, c) in Cin && c != a && dependency(b, c) - dependency(b, a) < sigma_r }
        }
        return C1 + C2 + Cbout + Cbin
    }
}