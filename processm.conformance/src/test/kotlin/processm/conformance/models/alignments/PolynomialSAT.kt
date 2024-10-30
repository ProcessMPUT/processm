package processm.conformance.models.alignments

import org.junit.jupiter.api.assertThrows
import processm.core.log.hierarchical.Trace
import processm.core.models.causalnet.*
import processm.helpers.mapToSet
import kotlin.math.absoluteValue
import kotlin.test.Test
import kotlin.test.assertTrue

class PolynomialSAT {

    @Test
    fun `sat - satsifiable`() {
        val cnet = buildSAT(listOf(setOf(1, 2, -3), setOf(1, -2, 3), setOf(-1, 2, 3), setOf(-1), setOf(-2), setOf(-3)))
        val alignment = AStar(cnet).align(Trace(emptySequence()))
        val activities = alignment.steps.mapToSet { (it.modelMove as DecoupledNodeExecution?)?.activity?.name }
        assertTrue { "1 False" in activities }
        assertTrue { "2 False" in activities }
        assertTrue { "3 False" in activities }
    }

    @Test
    fun `sat - unsatsifiable`() {
        val cnet = buildSAT(listOf(setOf(1, 2, -3), setOf(-1), setOf(-2), setOf(3)))
        assertThrows<IllegalStateException> { AStar(cnet).align(Trace(emptySequence())) }
    }

    /**
     * Represents the given propositional formula in the conjunctive normal form as a Causal net such that it has
     * a valid binding sequence if, and only if, the formula is satisfiable. This is much better than the transformation
     * implemented in [SATTest], as the transformation is polynomial, not exponential.
     *
     * @param formula Each member of the list represents a clause (i.e., a disjunction of literals). Each integer
     * represents a single literal, a positive value corresponds to a positive literal, whereas a negative value to
     * a negative literal. E.g., [{-1, 2}, {3, -4}] corresponds to the formula (~1 v 2) ^ (3 v ~4).
     */
    fun buildSAT(formula: List<Set<Int>>): CausalNet {
        val atoms = HashSet<Int>()
        val literals = formula.flatten().toSet()
        for (clause in formula)
            for (literal in clause)
                atoms.add(literal.absoluteValue)
        val atomNodes = atoms.associateWith { atom -> Node(atom.toString()) }
        val valueNodes = literals.associateWith { literal ->
            Node(if (literal > 0) "$literal True" else "${-literal} False")
        }
        val clauseNodes = formula.indices.map { Node("clause$it") }
        val start = Node("start")
        val end = Node("end")
        return MutableCausalNet(start = start, end = end).apply {
            addInstance(*atomNodes.values.toTypedArray())
            addInstance(*valueNodes.values.toTypedArray())
            addInstance(*clauseNodes.toTypedArray())
            for (n in atomNodes.values)
                addDependency(start, n)
            for ((atom, node) in atomNodes.entries) {
                valueNodes[atom]?.let { addDependency(node, it) }
                valueNodes[-atom]?.let { addDependency(node, it) }
            }
            for ((idx, clause) in formula.withIndex()) {
                val clauseNode = clauseNodes[idx]
                for (literal in clause)
                    addDependency(valueNodes[literal]!!, clauseNode)
                addDependency(clauseNode, clauseNode)
                addDependency(clauseNode, end)
            }
            // start to the atoms
            addSplit(Split(outgoing[start]!!))
            for (d in outgoing[start]!!)
                addJoin(Join(setOf(d)))
            // atoms to values
            for (node in atomNodes.values)
                for (d in outgoing[node]!!) {
                    addSplit(Split(setOf(d)))
                    addJoin(Join(setOf(d)))
                }
            // values to clauses
            for (node in valueNodes.values)
                addSplit(Split(outgoing[node]!!))
            for (clauseNode in clauseNodes) {
                val selfLoop = Dependency(clauseNode, clauseNode)
                addSplit(Split(setOf(selfLoop)))
                val rest = incoming[clauseNode]!! - setOf(selfLoop)
                for (d in rest) {
                    addJoin(Join(setOf(d)))
                    addJoin(Join(setOf(d, selfLoop)))
                }
            }
            // clauses to end
            for (node in incoming[end]!!)
                addSplit(Split(setOf(node)))
            addJoin(Join(incoming[end]!!))
        }
    }
}