package processm.etl.metamodel

import org.jgrapht.Graph
import org.jgrapht.traverse.TopologicalOrderIterator

/**
 * Represents business perspective as a tree of business entities' classes.
 *
 * @param classes Classes tree stored as <ChildClass, ParentClass> map. The root class should have null as a parent.
 * @param identifyingClasses Set of classes for which the belonging objects uniquely identify each business process instance.
 */
class DAGBusinessPerspectiveDefinition<TClass>(private val classesGraph: Graph<TClass, String>, caseNotionClassesSelector: ((Graph<TClass, String>) -> Set<TClass>)? = null) : Iterable<TClass> {
    /**
     * The root class for which the business perspective is build around.
     */
//    val rootClass = predecessingClasses.filterValues { it.isEmpty() }.keys.first()
    val caseNotionClasses: List<TClass> = caseNotionClassesSelector?.invoke(this.classesGraph)?.sortedBy { this.indexOf(it) } ?: this.toList()

    fun isCaseNotionClass(classId: TClass) = caseNotionClasses.contains(classId)

    /**
     * Set of classes for which the multiple object can belong to the same business process instance.
     */
//    val convergingClasses = emptySet<TClass>()
    override fun iterator(): Iterator<TClass> = TopologicalOrderIterator(classesGraph)

    /**
     * Returns all children of the provided class.
     *
     * @param className The parent class for which child classes should be returned.
     */
//    fun getChildren(className: TClass) = predecessingClasses[className].orEmpty()

    /**
     * Indicates whether direct relationship exists between two provided classes.
     */
//    fun hasRelation(childClass: TClass, parentClass: TClass) = classesGraph.containsEdge(childClass, parentClass)

    fun getSuccessors(classId: TClass) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeSource(it) == classId }
        .map { classesGraph.getEdgeTarget(it) }

    fun getPredecessors(classId: TClass) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeTarget(it) == classId }
        .map { classesGraph.getEdgeSource(it) }

    fun modifyIdentifyingObjects(caseNotionClassesSelector: (Graph<TClass, String>) -> Set<TClass>) = DAGBusinessPerspectiveDefinition(classesGraph, caseNotionClassesSelector)

//    val rootClasses = classesGraph.vertexSet().filter { classesGraph.inDegreeOf(it) == 0 }
}