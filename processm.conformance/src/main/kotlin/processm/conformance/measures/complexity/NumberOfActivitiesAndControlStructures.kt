package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel

/**
 * NOAC = Number of activities and control structures. All activities are counted, including silent and artificial ones.
 * For the control structures counted please refer to the [ProcessModel.controlStructures] property of the respective model.
 * See J.Cardoso, J.Mendling, G.Neumann, and H.A.Reijers, “A Discourse on Complexity of Process Models,”
 * in Proceedings of the 2006 International Conference on Business Process Management Workshops, Berlin,
 * Heidelberg, 2006, pp. 117–128.
 */
class NumberOfActivitiesAndControlStructures : Measure<ProcessModel, Int> {
    override fun invoke(artifact: ProcessModel): Int =
        artifact.activities.count() + artifact.controlStructures.count { it !is Activity /* do not count activities twice */ }
}