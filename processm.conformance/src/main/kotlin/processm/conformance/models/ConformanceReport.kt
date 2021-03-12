package processm.conformance.models

import processm.core.log.hierarchical.Log
import processm.core.models.commons.ProcessModel

data class ConformanceReport(
    val process: ProcessModel,
    val log: Log,
    val steps: ConformanceModel
)
