package processm.enhancement.kpi

import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import processm.conformance.models.alignments.Alignment
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.helpers.map2d.Map2D
import processm.helpers.stats.Distribution

/**
 * KPI report consisting of KPIs at the log, trace, and event scopes.
 */
@Serializable
data class Report(
    /**
     * The log-scope KPIs retrieved form raw numeric attributes of the log.
     * Keys correspond to the attribute/KPI names; values to the KPI values.
     */
    val logKPI: Map<String, @Contextual Distribution>,
    /**
     * The trace-scope KPIs retrieved from raw numeric attributes of the traces.
     * Keys correspond to the attribute/KPI names; values to the distributions of KPI values among traces.
     */
    val traceKPI: Map<String, @Contextual Distribution>,
    /**
     * The event-scope KPIs retrieved from raw numeric attributes of the events.
     * The first key corresponds to the attribute/KPI name;
     * the second key corresponds to the activity in the [model], null for the unaligned events;
     * value corresponds to the distribution of KPI values among events.
     */
    @Serializable(with = DoublingMap2DStringActivityDistributionSerializer::class)
    val eventKPI: Map2D<String, Activity?, Distribution>,
    /**
     * The event-scope KPIs for [CausalArc]s of the model.
     */
    @Serializable(with = DoublingMap2DStringCausalArcDistributionSerializer::class)
    val arcKPI: Map2D<String, CausalArc, Distribution>,
    /**
     * The model-related KPIs, such as Halstead complexity metric
     */
    val modelKPI: Map<String, Int>,

    /**
     * Alignments corresponding used to calculate this report.
     */
    val alignments: List<Alignment>,

    /**
     * `true` if concept drift was detected, `false` if it was not detected, `null` if it was not checked
     */
    var hasConceptDrift: Boolean? = null
) {
    companion object {
        /**
         * A kotlinx/serialization serializer configuration for this class.
         */
        @Deprecated("Configure serializer based on the SerializersModuleProvider Java service")
        val Json = Json {
            allowStructuredMapKeys = true
            serializersModule = ReportSerializersModuleProvider().getSerializersModule()
        }

        fun fromJson(json: String): Report = Json.decodeFromString(serializer(), json)
    }

    fun toJson(): String = Json.encodeToString(this)
}

