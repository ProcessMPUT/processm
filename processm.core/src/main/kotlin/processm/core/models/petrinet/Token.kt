package processm.core.models.petrinet

/**
 * A token for a Petri net. May hold extra information.
 * @property producer The transition that produced this token. Null for the token created in process initialization.
 */
@JvmInline
value class Token(
    val producer: Transition?
)
