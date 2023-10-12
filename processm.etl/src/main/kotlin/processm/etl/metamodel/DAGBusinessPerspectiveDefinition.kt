package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jgrapht.Graph

/**
 * Represents business perspective as a DAG consisting of business entities' classes.
 * Case notion specifies set of classes used to identify business process instances.
 *
 * @param classesGraph Graph (DAG) consisting of classes and relations included in the business perspective.
 * @param caseNotionClassesSelector Selector method used to specify classes which are part of case notion.
 */
class DAGBusinessPerspectiveDefinition(
    private val classesGraph: Graph<EntityID<Int>, String>,
    caseNotionClassesSelector: ((Graph<EntityID<Int>, String>) -> Set<EntityID<Int>>)? = null
) {
    /**
     * Identifying classes as per [https://doi.org/10.1007/s10115-019-01430-6]
     */
    val caseNotionClasses: Set<EntityID<Int>> =
        caseNotionClassesSelector?.invoke(this.classesGraph) ?: this.classesGraph.vertexSet()

    val convergingClasses: Set<EntityID<Int>> = classesGraph.vertexSet() - caseNotionClasses.toSet()

    /**
     * Returns business perspective classes that the specified class depends on.
     */
    fun getSuccessors(classId: EntityID<Int>) = classesGraph
        .edgesOf(classId)
        .filter { classesGraph.getEdgeSource(it) == classId }
        .map { classesGraph.getEdgeTarget(it) }

    /**
     * Generates an SQL query returning traces (as in https://doi.org/10.1007/s10115-019-01430-6)
     * represent as arrays of identifiers of object versions (from the table [processm.dbmodels.models.ObjectVersion])
     *
     * The query is quite complex, in particular relying heavily on GROUP BY. It may be the case that it will turn out
     * to be too expensive in practice.
     */
    internal fun generateSQLquery(): String {
        val from = StringBuilder()
        val where = StringBuilder()
        val classes = HashMap<EntityID<Int>, String>()
        for (cls in classesGraph.vertexSet()) {
            val table = "c${classes.size}"
            classes[cls] = table
            from.append("object_versions AS $table,")
            where.append(" $table.class_id = ${cls.value} AND")
        }
        val relations = HashMap<String, String>()
        for (e in classesGraph.edgeSet()) {
            val table = "r${relations.size}"
            relations[e] = table
            from.append("relations AS $table,")
            val sourceTable = classes[classesGraph.getEdgeSource(e)]
            val targetTable = classes[classesGraph.getEdgeTarget(e)]
            where.append(" $table.source_object_version_id = $sourceTable.id AND $table.target_object_version_id = $targetTable.id AND")
        }
        from.deleteCharAt(from.length - 1)
        where.delete(where.length - 3, where.length)
        val select = StringBuilder()
        val groupBy = StringBuilder()
        select.append("ARRAY[")
        caseNotionClasses.forEach {
            val table = classes[it]
            select.append("ROW($table.class_id,$table.object_id)::remote_object_identifier,")
            groupBy.append("$table.class_id,$table.object_id,")
        }
        select.deleteCharAt(select.length - 1)
        select.append("]")
        groupBy.deleteCharAt(groupBy.length - 1)
        convergingClasses.forEach {
            val table = classes[it]
            select.append("|| ARRAY_AGG(ROW($table.class_id,$table.object_id)::remote_object_identifier)")
        }
        return """
            WITH case_identifiers(ci) AS (
                SELECT DISTINCT $select
                FROM $from
                WHERE $where
                GROUP BY $groupBy
            )
            SELECT ARRAY_AGG(ovs.id)
            FROM  case_identifiers, object_versions AS ovs
            WHERE
            ROW(ovs.class_id, ovs.object_id)::remote_object_identifier = ANY((SELECT * FROM unnest(case_identifiers.ci)))
            GROUP BY case_identifiers.ci
        """.trimIndent()
    }
}