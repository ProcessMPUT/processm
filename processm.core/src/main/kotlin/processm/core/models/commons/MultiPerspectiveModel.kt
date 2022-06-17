package processm.core.models.commons

/**
 * A composition of multiple perspectives describing the same underlying business process, represented by [processModel]
 *
 * This is inspired by PM, chapter 9 "Mining additional perspectives", which hints that, e.g., a decision model is not an inherent property of a model,
 * but rather another view on the same underlying business process
 */
class MultiPerspectiveModel<DM : DecisionModel<*, *>>(val processModel: ProcessModel, val decisionModel: DM?)