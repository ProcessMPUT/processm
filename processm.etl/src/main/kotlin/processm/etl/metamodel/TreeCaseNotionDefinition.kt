package processm.etl.metamodel

/**
 * Represents business perspective as a tree of business entities' classes.
 *
 * @param classes Classes tree stored as <ChildClass, ParentClass> map. The root class should have null as a parent.
 * @param identifyingClasses Set of classes for which the belonging objects uniquely identify each business process instance.
 */
class TreeCaseNotionDefinition<TClass>(val classes: Map<TClass, TClass?>, val identifyingClasses: Set<TClass>) {
    /**
     * The root class for which the business perspective is build around.
     */
    val rootClass = classes.filterValues { it == null }.keys.first()

    /**
     * Set of classes for which the multiple object can belong to the same business process instance.
     */
    val convergingClasses = classes.filterKeys { !identifyingClasses.contains(it) }.keys

    /**
     * Returns all children of the provided class.
     *
     * @param className The parent class for which child classes should be returned.
     */
    fun getChildren(className: TClass) = classes.filterValues { it == className }.mapValues { it.value!! }

    /**
     * Indicates whether direct relationship exists between two provided classes.
     */
    fun hasRelation(childClass: TClass, parentClass: TClass) = classes[childClass] == parentClass
}