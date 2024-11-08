package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * NOAC = Number of activities and control structures. All activities are counted, including silent and artificial ones.
 * For the control structures counted please refer to the [ProcessModel.controlStructures] property of the respective model.
 * See J.Cardoso, J.Mendling, G.Neumann, and H.A.Reijers, “A Discourse on Complexity of Process Models,”
 * in Proceedings of the 2006 International Conference on Business Process Management Workshops, Berlin,
 * Heidelberg, 2006, pp. 117–128.
 */
object NumberOfActivitiesAndControlStructures : Measure<ProcessModel, Int> {
    override val URN: URN = URN("urn:processm:measures/number_of_activities_and_control_structures")
    override fun invoke(artifact: ProcessModel): Int =
        artifact.activities.count() + artifact.controlStructures.count { it !is Activity /* do not count activities twice */ }
}

typealias NOAC = NumberOfActivitiesAndControlStructures
