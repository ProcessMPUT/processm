package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.ProcessModel

/**
 * NOA = Number of activities in a process. All activities are counted, including silent and artificial ones.
 * See J.Cardoso, J.Mendling, G.Neumann, and H.A.Reijers, “A Discourse on Complexity of Process Models,”
 * in Proceedings of the 2006 International Conference on Business Process Management Workshops, Berlin,
 * Heidelberg, 2006, pp. 117–128.
 */
class NumberOfActivities : Measure<ProcessModel, Int> {
    override fun invoke(artifact: ProcessModel): Int = artifact.activities.count()
}
