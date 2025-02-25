package processm.conformance.rca.ml

import processm.conformance.rca.PropositionalSparseDataset


interface DecisionTreeClassifier : Classifier<Boolean> {
    override fun fit(sparseDataset: PropositionalSparseDataset): DecisionTreeModel
}