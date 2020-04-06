package processm.core.models.commons

/**
 * A common interface for decision points of a model. It is almost empty, as there is a very wide variety of possible
 * decisions, and providing an unified representation of them is more like translating all models to a common representation.
 */
interface AbstractDecisionPoint {

    /**
     * True if there is real decision to make here, e.g., an exclusive choice
     */
    val isStrict: Boolean
}