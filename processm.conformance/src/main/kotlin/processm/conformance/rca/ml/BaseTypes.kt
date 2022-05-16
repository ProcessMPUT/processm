package processm.conformance.rca.ml

import processm.conformance.rca.PropositionalSparseDataset

/**
 * A base class for machine learning models (e.g., a concrete decision tree)
 */
interface Model {}

/**
 * A base class for classifiers (e.g., a class for training decision trees)
 */
interface Classifier {

    /**
     * Compute a model classifying the given [dataset] well
     */
    fun fit(dataset: PropositionalSparseDataset): Model
}