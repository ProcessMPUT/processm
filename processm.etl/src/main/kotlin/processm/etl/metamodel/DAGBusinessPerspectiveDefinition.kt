package processm.etl.metamodel

import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * Represents business perspective as a DAG consisting of business entities' classes.
 * Case notion specifies set of classes used to identify business process instances.
 *
 * @param classesGraph Graph (DAG) consisting of classes and relations included in the business perspective.
 * @param caseNotionClassesSelector Selector method used to specify classes which are part of case notion.
 */
class DAGBusinessPerspectiveDefinition<TClass>(private val classesGraph: Graph<TClass, String>, caseNotionClassesSelector: ((Graph<TClass, String>) -> Set<TClass>)? = null) : Iterable<TClass> {
    /**
     * Collection of classes included in case notion.
     */
    val caseNotionClasses: List<TClass> = caseNotionClassesSelector?.invoke(this.classesGraph)?.sortedBy { this.indexOf(it) } ?: this.toList()

    /**
     * Checks if the specified class is included in case notion (i.e. used as a part of business process instance identifier).
     */
    fun isClassIncludedInCaseNotion(classId: TClass) = caseNotionClasses.contains(classId)

    /**
     * Iterator returning classes included in the business perspective in topological order.
     */
    override fun iterator(): Iterator<TClass> = TopologicalOrderIterator(classesGraph)

    /**
     * Returns business perspective classes that the specified class depends on.
     */
    fun getSuccessors(classId: TClass) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeSource(it) == classId }
        .map { classesGraph.getEdgeTarget(it) }

    /**
     * Returns business perspective classes that depend on the specified class.
     */
    fun getPredecessors(classId: TClass) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeTarget(it) == classId }
        .map { classesGraph.getEdgeSource(it) }

    /**
     * Creates new instance of the class using the provided selector to specify case notion classes.
     */
    fun modifyIdentifyingObjects(caseNotionClassesSelector: (Graph<TClass, String>) -> Set<TClass>) = DAGBusinessPerspectiveDefinition(classesGraph, caseNotionClassesSelector)
}