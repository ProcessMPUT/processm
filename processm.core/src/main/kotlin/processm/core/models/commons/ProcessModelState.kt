package processm.core.models.commons

/**
 * Represents state of execution in a model. Currently empty, as this very much depends on the concrete representation of the model
 */
interface ProcessModelState {
    /**
     * Performs a shallow copy of this state. The copied object is guaranteed to not change
     * even if the internal representation of this object changes.
     */
    fun copy(): ProcessModelState
}
