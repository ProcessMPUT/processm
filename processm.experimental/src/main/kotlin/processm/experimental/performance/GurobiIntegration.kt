package processm.experimental.performance

import gurobi.*
import processm.core.helpers.mapToSet
import processm.core.models.causalnet.CausalNet
import processm.core.models.causalnet.Join
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.Split
import processm.core.models.commons.Activity
import processm.core.helpers.HashMapWithDefault
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.Collection
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.Iterable
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.Set
import kotlin.collections.any
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.emptyList
import kotlin.collections.filter
import kotlin.collections.filterIndexed
import kotlin.collections.filterKeys
import kotlin.collections.filterTo
import kotlin.collections.flatten
import kotlin.collections.get
import kotlin.collections.indices
import kotlin.collections.isNotEmpty
import kotlin.collections.iterator
import kotlin.collections.joinToString
import kotlin.collections.listOf
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.mapValues
import kotlin.collections.max
import kotlin.collections.orEmpty
import kotlin.collections.set
import kotlin.collections.setOf
import kotlin.collections.single
import kotlin.collections.singleOrNull
import kotlin.collections.sortedBy
import kotlin.collections.toList
import kotlin.collections.toMap
import kotlin.collections.toMutableList
import kotlin.collections.toTypedArray
import kotlin.collections.withIndex
import kotlin.collections.zip
import kotlin.math.roundToInt

fun <T> cartesianProduct(vararg args: Iterable<T>): Sequence<List<T>> = sequence {
    val iterators = args.map { it.iterator() }.toMutableList()
    val current = iterators.map { it.next() }.toMutableList()
    while (true) {
        yield(ArrayList(current))   //return a copy
        var finished = true
        for (idx in 0 until iterators.size) {
            if (iterators[idx].hasNext()) {
                current[idx] = iterators[idx].next()
                finished = false
                break
            } else {
                iterators[idx] = args[idx].iterator()
                current[idx] = iterators[idx].next()
            }
        }
        if (finished)
            break
    }
}

private fun <T> GRBModel.addNamedVars(
    name: String,
    type: Char,
    lb: Double? = null,
    ub: Double? = null,
    vararg indices: Iterable<T>
): Map<List<T>, GRBVar> {
    val keys = cartesianProduct(*indices).toList()
    val names = keys.map { suffix -> name + "_" + suffix.joinToString(separator = "_") { it.toString() } }.toList()
        .toTypedArray()
    val types = CharArray(names.size) { type }
    val lbs = if (lb != null) DoubleArray(names.size) { lb } else null
    val ubs = if (ub != null) DoubleArray(names.size) { ub } else null
    return (keys zip this.addVars(lbs, ubs, null, types, names)).toMap()
}

private fun shorten(name: String): String {
    if (name.length <= 254)
        return name
    return name.substring(0, 125) + "..." + name.substring(name.length - 125, name.length)
}

private fun <A, B> GRBModel.addNamedVars2(
    name: String,
    i1: Iterable<A>,
    i2: Iterable<B>,
    type: Char,
    lb: Double? = null,
    ub: Double? = null
): Map<Pair<A, B>, GRBVar> {
    val keys = cartesianProduct(i1, i2).map { it[0] as A to it[1] as B }.toList()
    val names = keys
        .map { suffix ->
            shorten("${name}_${suffix.first}_${suffix.second}")
        }
        .toList()
        .toTypedArray()
    val types = CharArray(names.size) { type }
    val lbs = if (lb != null) DoubleArray(names.size) { lb } else null
    val ubs = if (ub != null) DoubleArray(names.size) { ub } else null
    return (keys zip this.addVars(lbs, ubs, null, types, names)).toMap()
}

class GRBModelDSL(val model: GRBModel) {
    infix fun GRBVar.le(other: GRBVar): GRBConstr = model.addConstr(this, GRB.LESS_EQUAL, other, null)
    infix fun GRBVar.le(other: Double): GRBConstr = model.addConstr(this, GRB.LESS_EQUAL, other, null)
    infix fun GRBVar.le(other: GRBLinExpr): GRBConstr = model.addConstr(this, GRB.LESS_EQUAL, other, null)

    infix fun GRBVar.ge(other: GRBVar) = other le this@ge

    infix fun GRBVar.eq(other: GRBLinExpr): GRBConstr = other eq this@eq
    infix fun GRBVar.eq(other: Double): GRBConstr = model.addConstr(this, GRB.EQUAL, other, null)


    infix fun GRBConstr.named(name: String): GRBConstr {
        this.set(GRB.StringAttr.ConstrName, shorten(name))
        return this
    }

    infix fun GRBLinExpr.eq(other: GRBLinExpr): GRBConstr = model.addConstr(this, GRB.EQUAL, other, null)
    infix fun GRBLinExpr.eq(other: GRBVar): GRBConstr = model.addConstr(this, GRB.EQUAL, other, null)
    infix fun GRBLinExpr.eq(other: Double): GRBConstr = model.addConstr(this, GRB.EQUAL, other, null)
    infix fun GRBLinExpr.le(other: Double): GRBConstr = model.addConstr(this, GRB.LESS_EQUAL, other, null)
    infix fun GRBLinExpr.le(other: GRBLinExpr): GRBConstr = model.addConstr(this, GRB.LESS_EQUAL, other, null)


    // this breaks the contract I think
    operator fun GRBLinExpr.minus(other: GRBVar): GRBLinExpr {
        this.addTerm(-1.0, other)
        return this
    }


    operator fun GRBLinExpr.minus(other: Double): GRBLinExpr = this + (-other)


    operator fun Double.minus(other:GRBVar):GRBLinExpr {
        val result = GRBLinExpr()
        result.addTerm(-1.0, other)
        result.addConstant(this)
        return result
    }

    operator fun GRBVar.minus(other: Double): GRBLinExpr {
        val result = GRBLinExpr()
        result.addTerm(1.0, this)
        result.addConstant(-other)
        return result
    }

    operator fun GRBVar.minus(other: GRBLinExpr): GRBLinExpr {
        val result = GRBLinExpr()
        result.addTerm(1.0, this)
        result.multAdd(-1.0, other)
        return result
    }

    operator fun GRBLinExpr.plus(other: GRBLinExpr): GRBLinExpr {
        this.add(other)
        return this
    }


    operator fun GRBLinExpr.plus(other: Double): GRBLinExpr {
        this.addConstant(other)
        return this
    }

    fun max(c:Double, vararg vars:GRBVar): GRBVar {
        val aux = model.addVars(1, GRB.CONTINUOUS)[0]
        model.addGenConstrMax(aux, vars, c, null)
        return aux
    }

    fun <A, B> Map<Pair<A, B>, GRBVar>.sum(a: A?, b: B?): GRBLinExpr {
        val e = GRBLinExpr()
        if (a != null && b != null)
            e.addTerm(1.0, this[a to b])
        else
            for ((k, v) in this)
                if ((a == null || k.first == a) && (b == null || k.second == b))
                    e.addTerm(1.0, v)
        return e
    }

    fun <A, B> Map<Pair<A, B>, GRBVar>.sum(a: Collection<A>, b: Collection<B>): GRBLinExpr {
        val e = GRBLinExpr()
        for ((k, v) in this)
            if (k.first in a && k.second in b)
                e.addTerm(1.0, v)
        return e
    }
}

fun GRBModel.constraints(init: GRBModelDSL.() -> Unit) {
    GRBModelDSL(this).init()
}

fun GRBModel.pretty(): String {
    val result = StringBuilder()
    for (c in this.constrs) {
        result.append(c[GRB.StringAttr.ConstrName])
        result.append(": ")
        val lhs = getRow(c)
        for (i in 0 until lhs.size()) {
            val coeff = lhs.getCoeff(i)
            if (coeff > 0)
                result.append("+")
            if (coeff != 1.0) {
                if (coeff != -1.0) {
                    result.append(coeff)
                    result.append("*")
                } else
                    result.append("-")
            }
            result.append(lhs.getVar(i)[GRB.StringAttr.VarName])
            result.append(" ")
        }
        if (lhs.constant != 0.0)
            result.append(lhs.constant)
        val s = c[GRB.CharAttr.Sense]
        result.append(" ")
        result.append(
            if (s == GRB.LESS_EQUAL) "<=" else if (s == GRB.EQUAL) "==" else if (s == GRB.GREATER_EQUAL) ">=" else error(
                "Unknown $s"
            )
        )
        result.append(" ")
        result.append(c[GRB.DoubleAttr.RHS])
        result.appendln()
    }
    return result.toString()
}

data class ActivityBinding(val node: Node, val join: Join?, val split: Split?)

class GurobiModel(val cnet: CausalNet, val maxLen: Int, val maxTokensOnDependency: Int) {
    val env = GRBEnv()
    val flatJoins = cnet.joins.values.flatten()
    val flatSplits = cnet.splits.values.flatten()
    val model = GRBModel(env)
    val n = model.addNamedVars2("n", 1..maxLen, cnet.instances, GRB.BINARY)
    val join = model.addNamedVars2("join", 1..maxLen, flatJoins, GRB.BINARY)
    val split = model.addNamedVars2("split", 1..maxLen, flatSplits, GRB.BINARY)
    val s =
        model.addNamedVars2("s", 0..maxLen, cnet.dependencies, GRB.CONTINUOUS, 0.0, maxTokensOnDependency.toDouble())
    val l = model.addVar(2.0, maxLen.toDouble(), 0.0, GRB.CONTINUOUS, "l")

    init {
        model.constraints {
            //// C1: available joins
            for (i in 1..maxLen)
                for (j in flatJoins)
                    join[i to j]!! le n[i to j.target]!! named "C1 $i $j"
            //// C2: single join
            for (i in 2..maxLen)
                join.sum(i, null) eq n.sum(i, null) - n[i to cnet.start]!! named "C2 $i"
            ////C3: deps active in state for join
            for (i in 1..maxLen)
                for (j in flatJoins)
                    for (dep in j.dependencies)
                        s[i - 1 to dep]!! ge join[i to j]!! named "C3 $i $j $dep"
            //C4: update state
            for (dep in cnet.dependencies) {
                val depJoins = cnet.joins[dep.target].orEmpty().filterTo(HashSet()) { it.dependencies.contains(dep) }
                val depSplits = cnet.splits[dep.source].orEmpty().filterTo(HashSet()) { it.dependencies.contains(dep) }
                for (i in 1..maxLen)
                    s[i to dep]!! eq s[i - 1 to dep]!! - join.sum(setOf(i), depJoins) + split.sum(
                        setOf(i),
                        depSplits
                    ) named "C4 $i $dep"
            }
            //C5: single split
            for (i in 1..maxLen)
                split.sum(i, null) eq n.sum(i, null) - n[i to cnet.end]!! named "C5 $i"
            //C6: available splits
            for (i in 1..maxLen)
                for (s in flatSplits)
                    split[i to s]!! le n[i to s.source]!! named "C6 $i $s"
            //C7: start is first
            n[1 to cnet.start]!! eq 1.0 named "C7"
            //C8: there is end
            n.sum(null, cnet.end) eq 1.0 named "C8"
            //C9: first state is empty
            for ((k, v) in s.filterKeys { it.first == 0 })
                v eq 0.0 named "C9 $k"
            //C10: last state is empty
            for ((k, v) in s.filterKeys { it.first == maxLen })
                v eq 0.0 named "C10 $k"
            //C11: at most single node
            for (i in 1..maxLen)
                n.sum(i, null) le 1.0 named "C11 $i"
            //C13: length
            n.sum(null as Int?, null) eq l named "C13"
            //C14: empty follows empty
            for (i in 1 until maxLen)
                n.sum(i + 1, null) le n.sum(i, null) named "C14 ${i + 1} $i"
            //C15: empty follows end
            for (i in 1 until maxLen)
                n.sum(i + 1, null) le n.sum(i, null) - n[i to cnet.end]!! named "C15 ${i + 1} $i"
            //C16: one start
            for (i in 2..maxLen)
                n[i to cnet.start]!! eq 0.0 named "C16 $i"
        }
        addDistanceConstraints()
    }

    private fun addDistanceConstraints() {
        val fromStart = distance(cnet.start, cnet.outgoing.mapValues { it.value.mapToSet { it.target } })
        val fromEnd = distance(cnet.end, cnet.incoming.mapValues { it.value.mapToSet { it.source } })
        println(fromStart)
        println("FROM END $fromEnd")
        model.constraints {
            //C19
            for ((node, dst) in fromStart)
                for (i in 1..dst) {
                    n[i to node]!! eq 0.0 named "C19 $node $i"
                }
//            //C20
//            for ((node, dst) in fromEnd)
//                for (i in 1..maxLen) {
//                    val aux=(dst + i - 1).toDouble()
//                    n[i to node]!! le max(aux, l) - aux
//                }
            //C21
            for((node, dst) in fromEnd) {
                if(node != cnet.end) {
                    for (i in 1..maxLen-dst+1)
                        for (ip in 1 until i + dst) {
                      //      n[ip to cnet.end]!! le 1.0 - n[i to node]!!
                        }
                }
            }
        }
    }

    internal fun distance(start: Node, graph: Map<Node, Set<Node>>): Map<Node, Int> {
        val queue = ArrayDeque<Node>()
        val result = HashMap<Node, Int>()
        queue.add(start)
        result[start] = 0
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            val base = result[current]!!
            for (n in graph[current].orEmpty()) {
                result.compute(n) { _, v ->
                    if (v == null || v > base + 1) {
                        queue.add(n)
                        base + 1
                    } else if (n == start && v == 0) {
                        base + 1
                    } else
                        v
                }
            }
        }
        return result
    }

    private fun setupPrefix(prefix: List<Activity>) {
        // reset does not reset these, so we must handle it by ourselves to facilitate model reuse
        for (v in n.values) {
            v[GRB.DoubleAttr.ScenNLB] = 0.0
            v[GRB.DoubleAttr.ScenNUB] = 1.0
        }
        for ((i, node) in prefix.withIndex()) {
            val v = n[i + 1 to node]!!
            v[GRB.DoubleAttr.ScenNLB] = 1.0
            v[GRB.DoubleAttr.ScenNUB] = 1.0
            for (other in cnet.instances)
                if (other != node) {
                    val v = n[i + 1 to other]!!
                    v[GRB.DoubleAttr.ScenNLB] = 0.0
                    v[GRB.DoubleAttr.ScenNUB] = 0.0
                }
        }
    }

    private fun setupPrefixes(prefixes: List<List<Activity>>) {
        val nScenarios = prefixes.size
        model[GRB.IntAttr.NumScenarios] = nScenarios
        for ((scen, prefix) in prefixes.withIndex()) {
            model[GRB.IntParam.ScenarioNumber] = scen
            setupPrefix(prefix)
        }
    }

    private fun extractSolution(attr: GRB.DoubleAttr = GRB.DoubleAttr.ScenNX): List<ActivityBinding>? {
        try {
            val seq = ArrayList<ActivityBinding>()
            for (i in 1..maxLen) {
                val nodes =
                    cnet.instances.filter { node -> n[i to node]!![attr].roundToInt() == 1 }
                assert(nodes.size <= 1)
                if (nodes.size == 1) {
                    val node = nodes.single()
                    val split =
                        cnet.splits[node]?.singleOrNull { s -> split[i to s]!![attr].roundToInt() == 1 }
                    val join =
                        cnet.joins[node]?.singleOrNull { j -> join[i to j]!![attr].roundToInt() == 1 }
                    seq.add(ActivityBinding(node, join, split))
                } else
                    break
            }
            return seq
        } catch (ex: GRBException) {
            if (ex.errorCode == GRB.ERROR_DATA_NOT_AVAILABLE)
                return null
            else
                throw ex
        }
    }

    private class Callback(val n: Map<Pair<Int, Node>, GRBVar>, val prefixes: Map<List<Activity>, Set<Activity>>) :
        GRBCallback() {

        val result = HashMapWithDefault<List<Activity>, HashSet<Activity>> { HashSet() }
        val incompletePrefixes = HashSet(prefixes.keys)
        val visitedSolutions = ArrayList<List<Activity>>()

        override fun callback() {
            if (this.where == GRB.Callback.MIPSOL) {
                mipsol()
            }
        }

        private fun decodeSolution(): List<Node> {
            val vars = n.values.toTypedArray()
            val solution = getSolution(vars)
            return vars
                .filterIndexed { idx, v -> solution[idx].roundToInt() == 1 }
                .map { v -> n.entries.single { e -> e.value == v } }
                .sortedBy { it.key.first }
                .map { it.key.second }
        }

        private fun <T> List<T>.startsWith(prefix: List<T>): Boolean =
            this.size >= prefix.size && this.subList(0, prefix.size) == prefix

        private val forbidden = ArrayList<List<Activity>>()

        private fun forbid(prefix: List<Activity>): Boolean {
            if (forbidden.any { prefix.startsWith(it) })
                return false
            val vars = prefix.mapIndexed { idx, a -> n[idx + 1 to a]!! }
            //at least one must be 0, they're binary, so their sum must be at most n-1
            val expr = GRBLinExpr()
            expr.addTerms(DoubleArray(vars.size) { 1.0 }, vars.toTypedArray())
            addLazy(expr, GRB.LESS_EQUAL, vars.size - 1.0)
            forbidden.add(prefix)
            println("Forbidding $prefix")
            return true
        }

        private fun mipsol() {
            val solution = decodeSolution()
            visitedSolutions.add(solution)
            val activeExtendedPrefixes = ArrayList<List<Activity>>()
            val completedPrefixes = ArrayList<List<Activity>>()
            for (prefix in incompletePrefixes)
                if (solution.size > prefix.size && prefix == solution.subList(0, prefix.size)) {
                    activeExtendedPrefixes.add(solution.subList(0, prefix.size + 1))
                    result[prefix].add(solution[prefix.size])
                    if (result[prefix] == prefixes[prefix]) {
                        println("Completed $prefix")
                        completedPrefixes.add(prefix)
                    }
                }
            if (completedPrefixes.isNotEmpty()) {
                incompletePrefixes.removeAll(completedPrefixes)
                for (solution in visitedSolutions) {
                    if (completedPrefixes.any { prefix -> solution.startsWith(prefix) }) {
                        val longest = incompletePrefixes
                            .filter { prefix -> solution.startsWith(prefix) }
                            .map { it.size }
                            .max()
                        if (longest != null && longest < solution.size - 1) {
                            forbid(solution.subList(0, longest + 1))
                        }
                    }
                }
            }
            assert(activeExtendedPrefixes.isNotEmpty())
            val maxPrefixLength = activeExtendedPrefixes.map { it.size }.max()!!
            for (extendedPrefix in activeExtendedPrefixes.filter { it.size == maxPrefixLength }) {
                forbid(extendedPrefix)
            }
        }
    }

    fun solve2(prefixes: MutableMap<List<Activity>, Set<Activity>>): Map<List<Activity>, Set<Activity>> {
        //setupPrefixes(prefixes)
        val nScenarios = prefixes.size
        model[GRB.IntAttr.NumScenarios] = nScenarios
        for ((scen, tmp) in prefixes.entries.withIndex()) {
            val (prefix, ub) = tmp
            model[GRB.IntParam.ScenarioNumber] = scen
            setupPrefix(prefix)
        }

        model[GRB.IntParam.PreSparsify] = 1
        model[GRB.IntParam.PreDepRow] = 1
        model[GRB.IntParam.PreDual] = 2
        model[GRB.IntParam.Quad] = 0

        //model[GRB.IntParam.MIPFocus] = 1
        //model[GRB.IntParam.Presolve] = 2

        //model[GRB.DoubleParam.TimeLimit] = 60.0
        //TODO verify whether we need to use PoolSearchMode=2
        //model[GRB.IntParam.PoolSearchMode] = 2
        //model[GRB.IntParam.PoolSolutions] = 100
        //model[GRB.IntParam.SolutionLimit] = 100

        model[GRB.IntParam.LazyConstraints] = 1


        model.update()
        val cb = Callback(n, prefixes)
        model.setCallback(cb)
        model.optimize()

        return cb.result
    }

    fun solve(prefixes: List<List<Activity>> = listOf(emptyList())): List<List<ActivityBinding>?> {
        setupPrefixes(prefixes)

        model[GRB.IntParam.PreSparsify] = 1
        model[GRB.IntParam.PreDepRow] = 1
        model[GRB.IntParam.PreDual] = 2
        model[GRB.IntParam.Quad] = 0

        model[GRB.DoubleParam.TimeLimit] = 60.0

        //TODO: setup PoolSearchMode etc.

//    println(model.pretty())
        model.update()
        model.optimize()
        val result = prefixes.indices.map {
            model[GRB.IntParam.ScenarioNumber] = it
            extractSolution()
        }
        println(result)
        return result
    }
}
