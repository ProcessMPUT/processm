package processm.enhancement.kpi

import kotlinx.serialization.*
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.mapSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import processm.core.models.causalnet.DecoupledNodeExecution
import processm.core.models.commons.Activity
import processm.core.models.commons.CausalArc
import processm.core.models.processtree.ProcessTreeActivity
import processm.helpers.map2d.DoublingMap2D
import processm.helpers.serialization.SerializersModuleProvider
import processm.helpers.stats.Distribution

/**
 * Provides [SerializersModule] for [Report] class.
 */
class ReportSerializersModuleProvider : SerializersModuleProvider {
    @OptIn(InternalSerializationApi::class)
    override fun getSerializersModule(): SerializersModule = SerializersModule {
        polymorphic(Activity::class) {
            subclass(processm.core.models.causalnet.Node::class)
            subclass(processm.core.models.petrinet.Transition::class)
            subclass(ProcessTreeActivity::class)
            subclass(DecoupledNodeExecution::class)
            subclass(DummyActivity::class)
        }
        polymorphic(CausalArc::class) {
            subclass(processm.core.models.causalnet.Dependency::class)
            subclass(VirtualPetriNetCausalArc::class)
            subclass(VirtualProcessTreeCausalArc::class)
            subclass(Arc::class)
        }
        contextual(Distribution::class, Distribution.serializer())
    }
}

class DoublingMap2DStringActivityDistributionSerializer : KSerializer<DoublingMap2D<String, Activity?, Distribution>> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor
        get() = mapSerialDescriptor(
            ContextualSerializer(String::class).descriptor,
            mapSerialDescriptor(
                Activity::class.serializer().nullable.descriptor,
                ContextualSerializer(Distribution::class).descriptor
            )
        )

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(decoder: Decoder): DoublingMap2D<String, Activity?, Distribution> {
        decoder as JsonDecoder
        val map2d = DoublingMap2D<String, Activity?, Distribution>()
        val root = decoder.decodeJsonElement()
        for ((row, entry) in root.jsonObject.entries) {
            for ((col, value) in entry.jsonObject.entries) {
                val activity = if (col == "\u0000") null else DummyActivity(col)
                map2d[row, activity] =
                    decoder.json.decodeFromJsonElement(
                        decoder.serializersModule.getContextual(Distribution::class)!!,
                        value
                    )
            }
        }
        return map2d
    }

    override fun serialize(encoder: Encoder, value: DoublingMap2D<String, Activity?, Distribution>) {
        encoder as JsonEncoder
        val obj = buildJsonObject {
            for (row in value.rows) {
                val rowObject = buildJsonObject {
                    for ((col, value) in value.getRow(row)) {
                        put(
                            col?.toString() ?: "\u0000",
                            encoder.json.encodeToJsonElement(
                                encoder.serializersModule.getContextual(Distribution::class)!!,
                                value
                            )
                        )
                    }
                }
                put(row, rowObject)
            }
        }
        encoder.encodeJsonElement(obj)
    }
}

class DoublingMap2DStringCausalArcDistributionSerializer : KSerializer<DoublingMap2D<String, CausalArc, Distribution>> {
    @OptIn(InternalSerializationApi::class)
    override val descriptor: SerialDescriptor
        get() = mapSerialDescriptor(
            ContextualSerializer(String::class).descriptor,
            mapSerialDescriptor(
                CausalArc::class.serializer().nullable.descriptor,
                ContextualSerializer(Distribution::class).descriptor
            )
        )

    @OptIn(InternalSerializationApi::class)
    override fun deserialize(decoder: Decoder): DoublingMap2D<String, CausalArc, Distribution> {
        decoder as JsonDecoder
        val map2d = DoublingMap2D<String, CausalArc, Distribution>()
        val root = decoder.decodeJsonElement()
        for ((row, entry) in root.jsonObject.entries) {
            for ((col, value) in entry.jsonObject.entries) {
                val (source, target) = col.splitOnArrow()
                map2d[row, Arc(DummyActivity(source), DummyActivity(target))] =
                    decoder.json.decodeFromJsonElement(
                        decoder.serializersModule.getContextual(Distribution::class)!!,
                        value
                    )
            }
        }
        return map2d
    }

    override fun serialize(encoder: Encoder, value: DoublingMap2D<String, CausalArc, Distribution>) {
        encoder as JsonEncoder
        val obj = buildJsonObject {
            for (row in value.rows) {
                val rowObject = buildJsonObject {
                    for ((col, value) in value.getRow(row)) {
                        val arc = "${col.source.name.escapeArrow()}->${col.target.name.escapeArrow()}"
                        put(
                            arc,
                            encoder.json.encodeToJsonElement(
                                encoder.serializersModule.getContextual(Distribution::class)!!,
                                value
                            )
                        )
                    }
                }
                put(row, rowObject)
            }
        }
        encoder.encodeJsonElement(obj)
    }

    private fun String.escapeArrow(): String = replace("\\", "\\\\").replace("->", "\\->")

    private fun String.splitOnArrow(): Pair<String, String> {
        val left = StringBuilder()
        val right = StringBuilder()
        var current = left
        var i = 0
        var escapeMode = false
        while (i < length) {
            val ch = get(i)
            when (ch) {
                '\\' -> {
                    if (escapeMode) {
                        current.append(ch)
                        escapeMode = false
                    } else {
                        escapeMode = true
                    }
                }

                '-' -> {
                    if (startsWith("->", i)) {
                        if (escapeMode) {
                            current.append("->")
                            escapeMode = false
                            i += 1
                        } else {
                            assert(current === left)
                            current = right
                            i += 1
                        }
                    } else {
                        current.append(ch)
                        escapeMode = false
                    }
                }

                else -> {
                    current.append(ch)
                    escapeMode = false
                }
            }
            ++i
        }

        return left.toString() to right.toString()
    }
}

@Serializable
private data class DummyActivity(override val name: String) : Activity {
    override fun equals(other: Any?): Boolean = other is Activity && this.name == other.name

    override fun hashCode(): Int = this.name.hashCode()

    override fun toString(): String = name
}
