package processm.core.models.commons

/**
 * A composition of multiple perspectives describing the same underlying business process, represented by [processModel]
 */
class MultiPerspectiveModel(val processModel: AbstractModel, val decisionModel: AbstractDecisionModel?) {
}