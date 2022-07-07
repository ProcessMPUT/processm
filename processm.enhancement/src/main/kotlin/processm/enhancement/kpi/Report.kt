package processm.enhancement.kpi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import processm.core.helpers.stats.Distribution
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import processm.core.models.commons.Activity

/**
 * KPI report consisting of KPIs at the log, trace, and event scopes.
 */
@Serializable
data class Report(
    /**
     * The log-scope KPIs retrieved form raw numeric attributes of the log.
     * Keys correspond to the attribute/KPI names; values to the KPI values.
     */
    val logKPI: Map<String, Distribution>,
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
) {
    companion object {
        private val reportFormat = Json {
            allowStructuredMapKeys = true
            serializersModule = SerializersModule {
                polymorphic(Activity::class) {
                    subclass(processm.core.models.causalnet.Node::class)
                    subclass(processm.core.models.petrinet.Transition::class)
                    // FIXME: process trees require a custom serializer
                    //subclass(processm.core.models.processtree.Node::class)
                }
                polymorphic(Map2D::class) {
                    subclass(
                        DoublingMap2D::class,
                        DoublingMap2D.serializer(
                            String.serializer(),
                            PolymorphicSerializer(Activity::class).nullable,
                            Distribution.serializer()
                        ) as KSerializer<DoublingMap2D<*, *, *>>
                    )
                }
            }
        }

        fun fromJson(json: String): Report = reportFormat.decodeFromString(serializer(), json)
    }

    fun toJson(): String = reportFormat.encodeToString(this)
}




