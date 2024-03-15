package processm.enhancement.kpi

import kotlinx.serialization.KSerializer
import kotlinx.serialization.PolymorphicSerializer
import kotlinx.serialization.builtins.PairSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.processtree.ProcessTreeActivity
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.map2d.Map2D
import processm.helpers.serialization.SerializersModuleProvider
import processm.helpers.stats.Distribution

/**
 * Provides [SerializersModule] for [Report] class.
 */
class ReportSerializersModuleProvider : SerializersModuleProvider {
    override fun getSerializersModule(): SerializersModule = SerializersModule {
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
