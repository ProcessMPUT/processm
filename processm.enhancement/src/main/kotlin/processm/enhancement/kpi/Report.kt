package processm.enhancement.kpi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.*
import kotlinx.serialization.modules.*
import processm.conformance.models.alignments.Alignment
import processm.core.helpers.map2d.DoublingMap2D
import processm.core.helpers.map2d.Map2D
import processm.core.helpers.stats.Distribution
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.processtree.ProcessTreeActivity

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
    val eventKPI: Map2D<String, Activity?, Distribution>,
    /**
     * The event-scope KPIs computed as for [eventKPI], but assigned to [CausalArc]s of the model that are
     * originating in this particular event.
     */
    val outboundArcKPI: Map2D<String, CausalArc, Distribution>,
    /**
     * The event-scope KPIs computed as for [eventKPI], but assigned to [CausalArc]s of the model that are
     * terminating in this particular event.
     */
    val inboundArcKPI: Map2D<String, CausalArc, Distribution>,

    /**
     * Alignments corresponding used to calculate this report.
     */
    val alignments: List<Alignment>
) {
    companion object {
        /**
         * A kotlinx/serialization serializer configuration for this class.
         */
        val Json = Json {
            allowStructuredMapKeys = true
            serializersModule = SerializersModule {
                polymorphic(Activity::class) {
                    subclass(processm.core.models.causalnet.Node::class)
                    subclass(processm.core.models.petrinet.Transition::class)
                    subclass(ProcessTreeActivity::class)
                    subclass(DecoupledNodeExecution::class)
                }
                polymorphic(CausalArc::class) {
                    subclass(processm.core.models.causalnet.Dependency::class)
                    subclass(VirtualPetriNetCausalArc::class)
                    subclass(VirtualProcessTreeCausalArc::class)
                }
                polymorphic(Map2D::class) {
                    subclass(
                        DoublingMap2D::class,
                        DoublingMap2D.serializer(
                            String.serializer(),
                            object : KSerializer<Any?> {

                                private val baseSerializer = PairSerializer(
                                    PolymorphicSerializer(Activity::class).nullable,
                                    PolymorphicSerializer(CausalArc::class).nullable
                                )
                                override val descriptor: SerialDescriptor
                                    get() = baseSerializer.descriptor

                                override fun deserialize(decoder: Decoder): Any? {
                                    val (s, t) = baseSerializer.deserialize(decoder)
                                    return s ?: t
                                }

                                override fun serialize(encoder: Encoder, value: Any?) {
                                    if (value is Activity?)
                                        baseSerializer.serialize(encoder, value to null)
                                    else {
                                        check(value is CausalArc)
                                        baseSerializer.serialize(encoder, null to value)
                                    }
                                }

                            },
                            Distribution.serializer()
                        ) as KSerializer<DoublingMap2D<*, *, *>>
                    )
                }
            }
        }

        fun fromJson(json: String): Report = Json.decodeFromString(serializer(), json)
    }

    fun toJson(): String = Json.encodeToString(this)
}




