package processm.conformance.measures.complexity

import processm.conformance.measures.Measure
import processm.core.models.commons.ProcessModel
import processm.core.models.metadata.URN

/**
 * NOA = Number of activities in a process. All activities are counted, including silent and artificial ones.
 * See J.Cardoso, J.Mendling, G.Neumann, and H.A.Reijers, “A Discourse on Complexity of Process Models,”
 * in Proceedings of the 2006 International Conference on Business Process Management Workshops, Berlin,
 * Heidelberg, 2006, pp. 117–128.
 */
object NumberOfActivities : Measure<ProcessModel, Int> {
    override val URN: URN = URN("urn:processm:measures/number_of_activities")
    override fun invoke(artifact: ProcessModel): Int = artifact.activities.count()
}

typealias NOA = NumberOfActivities
