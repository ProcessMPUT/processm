package processm.core.models.commons

/**
 * [Row] a description of a single example
 * [Dataset] a collection of [Row]s
 * [Decision] the result of passing a single [Row] through the learned [DecisionModel]
 */
interface DecisionLearner<in Row, in Dataset, out Decision> {
    /**
     * Use the provided [dataset] to train a [DecisionModel]
     */
    fun fit(dataset: Dataset): DecisionModel<Row, Decision>
}

/**
 * A decision model for providing explanations
 */
interface DecisionModel<in Row, out Decision> {

    /**
     * Provide decision for the given [row]
     */
    fun predict(row: Row): Decision

}
