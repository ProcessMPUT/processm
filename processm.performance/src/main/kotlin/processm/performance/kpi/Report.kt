package processm.performance.kpi

import processm.core.helpers.map2d.Map2D
import processm.core.log.hierarchical.Log
import processm.core.models.commons.Activity
import processm.core.models.commons.ProcessModel

/**
 * KPI report. For the given [log], possibly produces using a PQL query, and process [model] consists KPIs at the log,
 * trace, and event scopes.
 */
data class Report(
    /**
     * The input log, for which the report corresponds to.
     */
    val log: Log,
    /**
     * The input model, for which the report corresponds to.
     */
    val model: ProcessModel,
    /**
     * The log-scope KPIs retrieved form raw numeric attributes of the log.
     * Keys correspond to the attribute/KPI names; values to the KPI values.
     */
    val logKPI: Map<String, Double>,
    /**
     * The trace-scope KPIs retrieved from raw numeric attributes of the traces.
     * Keys correspond to the attribute/KPI names; values to the distributions of KPI values among traces.
     */
    val traceKPI: Map<String, Distribution>,
    /**
     * The event-scope KPIs retrieved from raw numeric attributes of the events.
     * The first key corresponds to the attribute/KPI name;
     * the second key corresponds to the activity in the [model], null for the unaligned events;
     * value corresponds to the distribution of KPI values among events.
     */
    val eventKPI: Map2D<String, Activity?, Distribution>
)
