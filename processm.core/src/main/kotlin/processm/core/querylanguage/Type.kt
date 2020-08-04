package processm.core.querylanguage

/**
 * A type of a PQL expression. Every data type includes a special value, called the null value. All comparisons to null
 * yield false, except for a special is operator.
 */
enum class Type {
    /**
     * Any of [String], [Number], [Datetime], and [Boolean].
     * Use of [Any] is allowed in [IExpression.expectedChildrenTypes] but disallowed in [IExpression.type].
     */
    Any,

    /**
     * The type is unknown at parse time and needs to be determined at run time.
     * Use of [Unknown] is disallowed in [IExpression.expectedChildrenTypes] but allowed in [IExpression.type].
     */
    Unknown,

    /**
     * An UTF-8-encoded text
     */
    String,

    /**
     * A double precision floating point number compliant with IEEE 754-2019
     */
    Number,

    /**
     * A timestamp with millisecond precision and timezone information compliant with ISO 8601-1:2019
     */
    Datetime,

    /**
     * A Boolean-algebra true or false
     */
    Boolean,

    /**
     * An universally unique identifier
     */
    UUID
}