package processm.conformance.models.alignments

import processm.conformance.models.ConformanceModel
import processm.core.helpers.toSubscript
import processm.core.helpers.toSuperscript

/**
 * The alignment of a model and a log as described in e.g.,
 * Sebastiaan J. van Zelst, Alfredo Bolt, and Boudewijn F. van Dongen,
 * Tuning Alignment Computation: An Experimental Evaluation
 */
data class Alignment(
    val steps: List<Step>,
    val cost: Int
) : ConformanceModel {
    override fun toString(): String = buildString {
        for (step in steps) {
            append(step.logMove?.conceptName?.toSuperscript() ?: "⁼")
            append(step.modelMove?.name?.toSubscript() ?: "₌")
        }
//        append('|')
//        for (step in steps) {
//            append((step.logMove?.conceptName ?: "").padEnd(step.modelMove?.name?.length ?: 0))
//            append('|')
//        }
//        append('\n')
//        append("-".repeat(this.length - 1))
//        append("\n|")
//        for (step in steps) {
//            append((step.modelMove?.name ?: "").padEnd(step.logMove!!.conceptName?.length ?: 0))
//            append('|')
//        }
    }
}
