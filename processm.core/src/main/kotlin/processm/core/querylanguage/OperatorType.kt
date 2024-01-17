package processm.core.querylanguage

/**
 * Represents a type of a PQL operator.
 */
enum class OperatorType {
    /**
     * A prefix operator, e.g., not.
     */
    Prefix,

    /**
     * An infix operator, e.g., +, -, *, /.
     */
    Infix,

    /**
     * A postfix operator, e.g., is null, is not null.
     */
    Postfix
}