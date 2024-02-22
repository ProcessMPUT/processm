package processm.helpers.ternarylogic

/**
 * Ternary-logic value.
 */
enum class Ternary {
    Unknown,
    True,
    False;

    companion object {
        /**
         * Converts Boolean value into ternary logic value.
         */
        fun Boolean.toTernary(): Ternary = if (this) True else False
    }
}
