package processm.core.querylanguage

import java.util.*
import kotlin.NoSuchElementException

/**
 * Represents an attribute in a PQL query.
 */
class Attribute(attribute: String, override val line: Int, override val charPositionInLine: Int) : Expression() {
    companion object {
        private val pqlAttributePattern =
            Regex("^(?:(([\\^]{0,2})(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?((?:\\w+:)?\\w+))|(\\[([\\^]{0,2})(?:(l(?:og)?|t(?:race)?|e(?:vent)?):)?(.+?)]))$")

        private val standardAttributes: EnumMap<Scope, Set<Pair<String, String?>>> =
            EnumMap<Scope, Set<Pair<String, String?>>>(Scope::class.java).also {
                it[Scope.Log] = setOf(
                    "concept" to "name",
                    "identity" to "id",
                    "lifecycle" to "model",
                    "db" to "id",
                    "xes" to "version",
                    "xes" to "features"
                )
                it[Scope.Trace] = setOf(
                    "concept" to "name",
                    "cost" to "currency",
                    "cost" to "total",
                    "identity" to "id",
                    "classifier" to null
                )
                it[Scope.Event] = setOf(
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
            }
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

    override val type: Type
        get() = if (this.isStandard && !this.isClassifier) {
            when (this.standardName) {
                "concept:name", "concept:instance",
                "cost:currency",
                "identity:id",  // TODO: change type to Type.ID / Type.UUID in issue #81
                "lifecycle:model", "lifecycle:transition", "lifecycle:state",
                "org:resource", "org:role", "org:group",
                "xes:version", "xes:features" -> Type.String
                "db:id", "cost:total" -> Type.Number
                "time:timestamp" -> Type.Datetime
                else -> throw IllegalArgumentException("Line $line position $charPositionInLine: Unknown type of attribute $standardName.")
            }
        } else {
            Type.Unknown
        }

    override val expectedChildrenTypes: Array<Type>
        get() = emptyArray()

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
        get() = (scope == Scope.Trace || scope == Scope.Event) &&
                standardName.startsWith("classifier:") || !isStandard && name.startsWith("classifier:")

    init {
        val match = pqlAttributePattern.matchEntire(attribute)
        assert(match !== null)

        val offset = if (match!!.groups[1] !== null) 2 else 6
        hoistingPrefix = match!!.groups[offset]!!.value
        scope = Scope.parse(match.groups[offset + 1]?.value)
        name = match.groups[offset + 2]!!.value

        assert(attribute.startsWith("[") == attribute.endsWith("]"))

        if (offset == 2) {
            // standard attribute
            standardName = standardAttributes[scope]!!
                .firstOrNull {
                    // the classifiers
                    if (it.second === null) name.startsWith("${it.first}:") || name.startsWith("${it.first[0]}:")
                    // the remaining standard attributes
                    else name == "${it.first}:${it.second}" || name == it.second
                }?.run { "$first:${second ?: name.substringAfterLast(':')}" }
                ?: throw NoSuchElementException(
                    "Line $line position $charPositionInLine: No such attribute: $attribute. Try using the square-bracket syntax for non-standard attributes."
                )
        } else {
            // other attribute
            standardName = ""
        }
    }

    override fun equals(other: Any?): Boolean =
        if (other !is Attribute) false
        else this.toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String =
        if (isStandard) "$hoistingPrefix$scope:$standardName"
        else "[$hoistingPrefix$scope:$name]"
}