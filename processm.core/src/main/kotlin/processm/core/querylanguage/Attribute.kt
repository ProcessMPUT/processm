package processm.core.querylanguage

/**
 * Represents an attribute in a PQL query.
 */
class Attribute(attribute: String, override val line: Int, override val charPositionInLine: Int) : Expression() {
    companion object {
        private val pqlAttributePattern =
            Regex("^(?:(([\\^]{0,2})(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?((?:\\w+:)?\\w+))|(\\[([\\^]{0,2})(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?(.+?)]))$")

        private val standardAttributes: Map<Scope, Set<Pair<String, String?>>> = mapOf(
            Scope.Log to setOf(
                "concept" to "name",
                "identity" to "id",
                "lifecycle" to "model",
                "db" to "id",
                "xes" to "version",
                "xes" to "features"
            ),
            Scope.Trace to setOf(
                "concept" to "name",
                "cost" to "currency",
                "cost" to "total",
                "identity" to "id",
                "classifier" to null
            ),
            Scope.Event to setOf(
                "concept" to "name",
                "concept" to "instance",
                "cost" to "currency",
                "cost" to "total",
                "identity" to "id",
                "lifecycle" to "transition",
                "lifecycle" to "state",
                "org" to "resource",
                "org" to "role",
                "org" to "group",
                "time" to "timestamp",
                "classifier" to null
            )
        )
    }

    /**
     * The prefix that raises the scope of this attribute.
     */
    val hoistingPrefix: String

    /**
     * The (non-hoisted) scope of this attribute.
     */
    override val scope: Scope?

    override fun calculateEffectiveScope(): Scope? =
        hoistingPrefix.fold(scope!!) { s, _ ->
            requireNotNull(s.upper) { "Line $line position $charPositionInLine: It is not supported to hoist a scope beyond the log scope." }
        }

    /**
     * The name of this attribute as specified in PQL.
     */
    val name: String

    /**
     * The standard name of this attribute (calculated).
     */
    val standardName: String

    /**
     * True if this is a standard attribute.
     */
    val isStandard: Boolean
        get() = standardName != ""

    /**
     * True if this is a classifier attribute.
     */
    val isClassifier: Boolean
        get() = standardName.startsWith("classifier:") && (scope == Scope.Trace || scope == Scope.Event)

    init {
        val match = pqlAttributePattern.matchEntire(attribute)
        assert(match !== null)

        val offset = if (match!!.groups[1] !== null) 2 else 6
        hoistingPrefix = match!!.groups[offset]!!.value
        scope = Scope.parse(match.groups[offset + 1]?.value)
        name = match.groups[offset + 2]!!.value

        assert(attribute.startsWith("[") == attribute.endsWith("]"))

        standardName = if (offset == 2) {
            standardAttributes[scope]?.firstOrNull {
                // the classifiers
                if (it.second === null) name.startsWith("${it.first}:") || name.startsWith("${it.first[0]}:")
                // the remaining standard attributes
                else name == "${it.first}:${it.second}" || name == it.second
            }?.run { "$first:${second ?: name.substringAfterLast(':')}" }
                ?: throw NoSuchElementException(
                    "Line $line position $charPositionInLine: No such attribute: $attribute. Try using the square-bracket syntax for non-standard attributes."
                )
        } else ""
    }

    override fun equals(other: Any?): Boolean =
        if (other !is Attribute) false
        else this.toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String =
        if (isStandard) "$hoistingPrefix$scope:$standardName"
        else "[$hoistingPrefix$scope:$name]"
}