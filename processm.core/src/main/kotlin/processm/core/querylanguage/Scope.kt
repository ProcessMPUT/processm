package processm.core.querylanguage

/**
 * Represents a scope in a PQL query.
 */
enum class Scope {
    /**
     * Log scope.
     */
    Log,

    /**
     * Trace scope.
     */
    Trace,

    /**
     * Event scope. This is default most of the time.
     */
    Event;

    companion object {
        /**
         * Transforms the given string into a [Scope]. The [default] argument sets the default when
         * empty string and null is supplied as the first argument.
         * @param text String to parse.
         * @param default The default value if [text] is null or empty.
         * @throws IllegalArgumentException If an incorrect string is supplied.
         */
        fun parse(text: String?, default: Scope = Event): Scope = when (text) {
            "", null -> default
            "log", "l" -> Log
            "trace", "t" -> Trace
            "event", "e" -> Event
            else -> throw IllegalArgumentException("Invalid scope $text.")
        }
    }

    /**
     * The one level up scope. Null for the top-most scope.
     */
    val upper: Scope?
        get() = when (this) {
            Log -> null
            Trace -> Log
            Event -> Trace
        }

    /**
     * The one level down scope. Null for the bottom-most scope.
     */
    val lower: Scope?
        get() = when (this) {
            Log -> Trace
            Trace -> Event
            Event -> null
        }

    /**
     * A single letter abbreviation for scope name. Useful as scope prefix in many applications.
     */
    val shortName: String
        get() = when (this) {
            Log -> "l"
            Trace -> "t"
            Event -> "e"
        }

    override fun toString(): String = when (this) {
        Log -> "log"
        Trace -> "trace"
        Event -> "event"
    }
}