package processm.core.querylanguage

import processm.core.log.attribute.Attribute.CONCEPT_INSTANCE
import processm.core.log.attribute.Attribute.CONCEPT_NAME
import processm.core.log.attribute.Attribute.COST_CURRENCY
import processm.core.log.attribute.Attribute.COST_TOTAL
import processm.core.log.attribute.Attribute.DB_ID
import processm.core.log.attribute.Attribute.IDENTITY_ID
import processm.core.log.attribute.Attribute.LIFECYCLE_MODEL
import processm.core.log.attribute.Attribute.LIFECYCLE_STATE
import processm.core.log.attribute.Attribute.LIFECYCLE_TRANSITION
import processm.core.log.attribute.Attribute.ORG_GROUP
import processm.core.log.attribute.Attribute.ORG_RESOURCE
import processm.core.log.attribute.Attribute.ORG_ROLE
import processm.core.log.attribute.Attribute.TIME_TIMESTAMP
import processm.core.log.attribute.Attribute.XES_FEATURES
import processm.core.log.attribute.Attribute.XES_VERSION
import java.util.*

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
            s.upper ?: throw PQLSyntaxException(PQLSyntaxException.Problem.NoHoistingBeyondLong, line, charPositionInLine)
        }

    override val type: Type
        get() = if (this.isStandard && !this.isClassifier) {
            when (this.standardName) {
                CONCEPT_NAME, CONCEPT_INSTANCE,
                COST_CURRENCY,
                LIFECYCLE_MODEL, LIFECYCLE_TRANSITION, LIFECYCLE_STATE,
                ORG_RESOURCE, ORG_ROLE, ORG_GROUP,
                XES_VERSION, XES_FEATURES -> Type.String
                DB_ID, COST_TOTAL -> Type.Number
                IDENTITY_ID -> Type.UUID
                TIME_TIMESTAMP -> Type.Datetime
                else -> throw PQLSyntaxException(
                    PQLSyntaxException.Problem.UnknownAttributeType,
                    line,
                    charPositionInLine,
                    standardAttributes
                )
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
        get() = standardName.startsWith("classifier:") ||
                !isStandard && (name.startsWith("classifier:") || name.startsWith("c:"))

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
                ?: throw PQLSyntaxException(
                    PQLSyntaxException.Problem.NoSuchAttribute,
                    line,
                    charPositionInLine,
                    attribute
                )
        } else {
            // other attribute
            standardName = ""
        }

        if (!(!isClassifier || effectiveScope != Scope.Log)) {
            throw PQLSyntaxException(PQLSyntaxException.Problem.ClassifierOnLog, line, charPositionInLine, this)
        }
    }

    fun dropHoisting(): Attribute = Attribute(
        if (isStandard) "$scope:$standardName" else "[$scope:$name]",
        line,
        charPositionInLine
    )

    override fun equals(other: Any?): Boolean =
        if (other !is Attribute) false
        else this.toString() == other.toString()

    override fun hashCode(): Int = toString().hashCode()

    override fun toString(): String =
        if (isStandard) "$hoistingPrefix$scope:$standardName"
        else "[$hoistingPrefix$scope:$name]"
}
