package processm.conformance.models.alignments

import org.junit.jupiter.api.AfterAll
import processm.core.helpers.allSubsets
import processm.core.helpers.mapToSet
import processm.core.log.Helpers
import processm.core.models.causalnet.*
import processm.core.models.petrinet.converters.toPetriNet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.Ignore
import kotlin.test.assertEquals

/**
 * A formula in CNF. An atom is denoted by an integer, a positive literal is a pair (true, atom) and a negative literal is a pair (false, atom).
 * A clause is represented as a list of such pairs (literals) and a formula is represented as a list of such lists (clauses).
 *
 * Glossary:
 * * an atom is a boolean variable;
 * * a literal is an atom or its negation;
 * * a clause is a disjunction of literals;
 * * a formula in CNF is a conjunction of clauses.
 */
typealias CNFForm = List<List<Pair<Boolean, Int>>>

class SATTest {

    companion object {
        val pool = Executors.newCachedThreadPool()

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    /**
     * Generates a satisfiable propositional logic formula in conjuctive normal form (CNF).
     *
     * @see CNFForm
     * @param nAtoms The number of atoms to be present in the formula, encoded as integers 0 to nAtoms-1
     * @param minUses For each atom, the minimal number of clauses containing the atom
     * @param maxUses For each atom, the maximal number of clauses containing the atom
     * @param minClauseSize The minimal number of literals in each clause
     * @param maxClauseSize The minimal number of literals in each clause. If maxClauseSize=minClauseSize=3, then the generated formulas are 3SAT.
     * @return A pair, the first element is the generated formula as CNFForm, the second is its model (i.e., an assignment of truth values to the atoms such that the formula is satisfied).
     * The positions in the model correspond to the atom identifiers, so if there's true in the i-th position it means that atom i is to be assigned true.
     */
    private fun gen(
        nAtoms: Int,
        rnd: Random,
        minUses: Int = 3,
        maxUses: Int = 4,
        minClauseSize: Int = 3,
        maxClauseSize: Int = 3
    ): Pair<CNFForm, List<Boolean>> {
        require(minClauseSize >= 2)
        require(maxClauseSize >= minClauseSize)
        require(nAtoms >= minClauseSize)
        require(maxUses >= minUses)
        require(minUses >= 1)
        val model = List(nAtoms) { rnd.nextBoolean() }
        val cnfForm = ArrayList<List<Pair<Boolean, Int>>>()
        val nClauses = IntArray(nAtoms)
        val mayUse = ArrayList<Int>()
        val mustUse = ArrayList<Int>()
        while (true) {
            mustUse.clear()
            mayUse.clear()
            for (atom in 0 until nAtoms)
                if (nClauses[atom] < maxUses) {
                    mayUse.add(atom)
                    if (nClauses[atom] < minUses)
                        mustUse.add(atom)
                }
            if (mayUse.isEmpty())
                break
            val clause = ArrayList<Pair<Boolean, Int>>()
            val clauseSize = rnd.nextInt(minClauseSize, maxClauseSize + 1).coerceAtMost(mayUse.size)
            for (i in 0 until clauseSize) {
                val atom =
                    if (mustUse.isNotEmpty()) mustUse[rnd.nextInt(mustUse.size)] else mayUse[rnd.nextInt(mayUse.size)]
                val sgn = when {
                    i==0 -> model[atom]
                    i==1 -> !model[atom]
                    else -> rnd.nextBoolean()
                }
                mayUse.remove(atom)
                mustUse.remove(atom)
                nClauses[atom] += 1
                clause.add(sgn to atom)
            }
            cnfForm.add(clause)
        }
        return cnfForm to model
    }

    private fun cnfFormToString(form: CNFForm): String =
        form.joinToString(separator = " ∧ ") { clause ->
            clause.joinToString(separator = " ∨ ", prefix = "(", postfix = ")") { literal ->
                (if (!literal.first) "¬" else "") + literal.second.toString()
            }
        }

    /**
     * The resulting CNet consists of four layers:
     * 1. The start node.
     * 2. Nodes corresponding 1:1 to the atoms of the formula.
     * 3. Nodes corresponding 1:1 to the clauses of the formula.
     * 4. The end node.
     *
     * The start node executes all the nodes in the second layer in parallel.
     * Each node in the second layer corresponds to an atom, denoted by a.
     * The node has a single join from the start node, and two splits: one executing in parallel all the nodes corresponding to the clauses containing the positive literal of a, and the other behaving respectively for the negative literal of a (i.e., ~a).
     * Each node in the third layer corresponds to a clause and has a single split to the end node.
     * Its joins are composed of the powerset (minus the empty set) of the incoming dependencies.
     *
     * Remark: deciding whether there is a valid binding sequence for the CNet is equivalent to deciding the satisfiability of the considered formula.
     * If the formula is satisfiable, it is possible to perfectly align any trace of form (start, arbitrary ordering of nodes of layer 2, arbitrary ordering of nodes of layer 3, end).
     *
     * By the construction, the resulting cnet may contain dead parts. Weeding them out requires finding all the valid binding sequences of the cnet,
     * which in turn is equivalent to enumerating all the models of the formula.
     */
    private fun cnfFormToCNet(form: CNFForm): CausalNet {
        val start=Node("start")
        val end=Node("end")
        val nClauses = form.size
        val nAtoms = form.maxOf { clause -> clause.maxOf { literal -> literal.second } } + 1
        val cNodes = List(nClauses) {Node("C${it}")}
        val aNodes = List(nAtoms) {Node("A${it}")}
        val result=MutableCausalNet(start=start, end=end)
        result.addInstance(*cNodes.toTypedArray())
        result.addInstance(*aNodes.toTypedArray())
        for(aIdx in 0 until nAtoms) {
            result.addJoin(Join(setOf(result.addDependency(result.start, aNodes[aIdx]))))
            val posClauses = form.mapIndexedNotNull { cIdx, clause ->
                if((true to aIdx) in clause)
                    cIdx
                else
                    null
            }
            val negClauses = form.mapIndexedNotNull { cIdx, clause ->
                if((false to aIdx) in clause)
                    cIdx
                else
                    null
            }
            val posSplit = posClauses.mapToSet { cIdx -> result.addDependency(aNodes[aIdx], cNodes[cIdx]) }
            if(posSplit.isNotEmpty())
                result.addSplit(Split(posSplit))
            val negSplit = negClauses.mapToSet { cIdx -> result.addDependency(aNodes[aIdx], cNodes[cIdx]) }
            if(negSplit.isNotEmpty())
                result.addSplit(Split(negSplit))
        }
        for(cIdx in 0 until nClauses) {
            val deps = form[cIdx].mapToSet { literal -> Dependency(aNodes[literal.second], cNodes[cIdx]) }
            for(join in deps.allSubsets(excludeEmpty = true))
                result.addJoin(Join(join))
            result.addSplit(Split(setOf(result.addDependency(cNodes[cIdx], result.end))))
        }
        result.addSplit(Split(aNodes.mapToSet { Dependency(result.start, it) }))
        result.addJoin(Join(cNodes.mapToSet { Dependency(it, result.end) }))
        return result
    }

    private fun test(nAtoms:Int) {
        val rnd = Random(42)
        val (form, model) = gen(nAtoms, rnd)
        println("Formula: ${cnfFormToString(form)}")
        println("Model: "+model.mapIndexed { atom, sgn -> (if (!sgn) "¬" else "") + atom.toString() })
        val cnet = cnfFormToCNet(form)
        val logText =cnet.start.name + " " +
                cnet.instances.filter { it.name[0] == 'A' }.joinToString(separator = " ") {it.name} + " " +
                cnet.instances.filter { it.name[0] == 'C' }.joinToString(separator = " ") {it.name} + " " +
                cnet.end.name
        val log = Helpers.logFromString(logText)
        val aligner = CompositeAligner(cnet.toPetriNet(), pool = SATTest.pool)
        val alignment = aligner.align(log.traces.first())
        assertEquals(0, alignment.cost)
    }

    @Test
    fun `test 3`() = test(3)

    @Test
    fun `test 5`() = test(5)

    @Test
    fun `test 6`() = test(6)

    @Ignore("Sometimes takes very long, sometimes finishes in a second or so")
    @Test
    fun `test 7`() = test(7)

    @Ignore("Takes too long")
    @Test
    fun `test 8`() = test(8)

    @Ignore("Takes too long")
    @Test
    fun `test 9`() = test(9)
}