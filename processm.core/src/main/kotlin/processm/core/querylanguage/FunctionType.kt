package processm.core.querylanguage

/**
 * The type of a PQL function.
 */
enum class FunctionType {
    /**
     * A scalar function that produces exactly one value for each input entity.
     */
    Scalar,

    /**
     * An aggregation function that produces exactly one value for a set of input entities.
     */
    Aggregation
}