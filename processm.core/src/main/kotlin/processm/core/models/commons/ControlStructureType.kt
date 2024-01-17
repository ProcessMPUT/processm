package processm.core.models.commons

/**
 * Represents the type of the control flow structure in a process model.
 */
enum class ControlStructureType {
    /**
     * A simple one-to-one dependency between activities.
     */
    Causality,

    /**
     * An exclusive-OR split.
     */
    XorSplit,

    /**
     * A parallel split.
     */
    AndSplit,

    /**
     * An inclusive-OR split.
     */
    OrSplit,

    /**
     * A general split that does not match XOR, OR, AND patterns.
     */
    OtherSplit,

    /**
     * An exclusive-OR join.
     */
    XorJoin,

    /**
     * A parallel join (a synchronization point).
     */
    AndJoin,

    /**
     * An inclusive-OR join.
     */
    OrJoin,

    /**
     * A general join that does not match XOR, OR, AND patterns.
     */
    OtherJoin
}
