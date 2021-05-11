package processm.conformance.models

/**
 * Represents the type of the log/model deviation.
 */
enum class DeviationType {
    /**
     * No deviation, the model conforms to the log.
     */
    None,

    /**
     * Log deviates from the model. E.g., an event in the log does not match the state of the model.
     */
    LogDeviation,

    /**
     * Model deviates from the log. E.g., the state of the model requires a move that does not occur in the log.
     */
    ModelDeviation
}
