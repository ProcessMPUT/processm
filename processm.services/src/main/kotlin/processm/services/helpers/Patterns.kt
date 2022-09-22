package processm.services.helpers

/**
 * Common regexes for validation.
 */
object Patterns {
    val email = Regex("^([a-zA-Z0-9._%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6})*\$")

    /**
     * Should have 1 lowercase letter, 1 uppercase letter, 1 number, and be at least 8 characters long
     */
    val password = Regex("(?=(.*[0-9]))((?=.*[A-Za-z0-9])(?=.*[A-Z])(?=.*[a-z]))^.{8,}\$")
}
