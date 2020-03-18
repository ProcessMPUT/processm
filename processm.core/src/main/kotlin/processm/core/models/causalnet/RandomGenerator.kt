package processm.core.models.causalnet

import processm.core.helpers.allSubsets
import kotlin.random.Random



/**
 * A random generator of Causal net models. No guarantees whatshoever, in particular it is very possible that the resulting
 * model will have some dead parts. Still, one should expect at least one (and possibly more) valid sequences.
 * Splits are randomly chosen between full AND, full XOR and full OR. Joins are always full OR (to accomodate any choice mades while generating splits)
 * Does not generate any kind of loops.
 */
class RandomGenerator(
    private val rnd: Random,
    private val nNodes: Int = 5,
    private val nAdditionalDependencies: Int = 4,
    private val pXOR: Double = .4,
    private val pAND: Double = .4
) {

    fun generate(): MutableModel {
        val nodes = List(nNodes) { Node((it + 'a'.toInt()).toChar().toString()) }
        val result = MutableModel(start = nodes[0], end = nodes[nodes.size - 1])
        result.addInstance(*nodes.toTypedArray())
        for (i in 1 until nNodes)
            result.addDependency(nodes[i - 1], nodes[i])
        for (i in 0 until nAdditionalDependencies) {
            while (true) {
                val x = rnd.nextInt(0, nNodes - 1)
                val y = rnd.nextInt(x + 1, nNodes)
                val dep = Dependency(nodes[x], nodes[y])
                if (result.outgoing[nodes[x]]?.contains(dep) != true) {
                    result.addDependency(dep)
                    break
                }
            }
        }
        for (n in nodes) {
            val o = result.outgoing[n]
            if (o != null) {
                if (o.size >= 2) {
                    val p = rnd.nextDouble(0.0, 1.0)
                    if (p <= pAND)
                        result.addSplit(Split(o))  //AND
                    else if (p - pAND <= pXOR)
                        o.forEach { result.addSplit(Split(setOf(it))) }    //XOR
                    else
                        o.allSubsets().filter { it.isNotEmpty() }.forEach { result.addSplit(Split(it.toSet())) }   //OR
                } else
                    result.addSplit(Split(o))

            }
            val i = result.incoming[n]
            i?.allSubsets()?.filter { it.isNotEmpty() }?.forEach { result.addJoin(Join(it.toSet())) }
        }
        return result
    }
}