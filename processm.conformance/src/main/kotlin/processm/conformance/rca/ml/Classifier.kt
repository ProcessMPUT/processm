package processm.conformance.rca.ml

import processm.conformance.rca.Feature
import processm.conformance.rca.PropositionalSparseDataset
import processm.core.models.commons.DecisionLearner
import processm.core.models.commons.DecisionModel


/**
 * A base class for classifiers (e.g., a class for training decision trees)
 */
interface Classifier<out Decision> : DecisionLearner<Map<Feature, Any>, PropositionalSparseDataset, Decision> {

    /**
     * Compute a model classifying the given [dataset] well
     */
    override fun fit(dataset: PropositionalSparseDataset): DecisionModel<Map<Feature, Any>, Decision>
}