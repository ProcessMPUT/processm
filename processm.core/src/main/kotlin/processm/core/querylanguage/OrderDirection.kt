package processm.core.querylanguage

/**
 * Represents the direction of order in a PQL query.
 */
enum class OrderDirection {
    /**
     * The ascending direction.
     */
    Ascending,

    /**
     * The descending direction.
     */
    Descending;

    companion object {
        /**
         * Parses the given string into an [OrderDirection].
         *
         * @param text The string to parse.
         * @param default The default value to return when [text] is null or empty string.
         * @return [OrderDirection]
         */
        fun parse(text: String?, default: OrderDirection = Ascending): OrderDirection = when (text) {
            null, "" -> default
            "asc", Ascending.name -> Ascending
            "desc", Descending.name -> Descending
            else -> throw IllegalArgumentException("Invalid order direction $text.")
        }
    }

    override fun toString(): String = when (this) {
        Ascending -> "asc"
        Descending -> "desc"
    }
}