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
         * Transforms the given string into a [Scope]. The [emptyStrInterpretation] argument sets the default when
         * empty string and null is supplied as the first argument.
         * @param str String to parse.
         * @param emptyStrInterpretation The default value if [str] is null or empty.
         * @throws IllegalArgumentException If an incorrect string is supplied.
         */
        fun parse(str: String?, emptyStrInterpretation: Scope = Event): Scope = when (str) {
            "", null -> emptyStrInterpretation
            "log", "l" -> Log
            "trace", "t" -> Trace
            "event", "e" -> Event
            else -> throw IllegalArgumentException("Invalid scope $str.")
        }
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