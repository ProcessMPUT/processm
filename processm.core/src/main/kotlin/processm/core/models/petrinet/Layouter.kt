package processm.core.models.petrinet

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.commons.collections4.bidimap.DualHashBidiMap
import processm.helpers.SerializableUUID
import java.util.*

@Serializable
data class Coordinates(val id: SerializableUUID, val x: Double, val y: Double)

@Serializable
data class Layout(val layout: List<Coordinates>)

/**
 * Compute a layout for the given [PetriNet] using `dot` from GraphViz
 */
class Layouter(val model: PetriNet) {

    private val id2num = DualHashBidiMap<UUID, Int>()

    private fun getNum(id: UUID) = id2num.computeIfAbsent(id) { id2num.size + 1 }

    private fun getUUID(n: Int) = id2num.getKey(n)

    private fun generateDotCode(): String = buildString {
        append("digraph test {\nrankdir=LR\nsplines=polyline\n")
        for (node in model.places) {
            append(getNum(node.id))
            append(" [shape=circle,label=\"\"];\n")
        }
        for (node in model.transitions) {
            append(getNum(node.id))
            append(" [shape=box")
            append(", label=\"")
            append(node.name.replace('"', '_'))
            append("\"")
            append("];\n")
            for (place in node.inPlaces) {
                append(getNum(place.id))
                append(" -> ")
                append(getNum(node.id))
                append(";\n")
            }
            for (place in node.outPlaces) {
                append(getNum(node.id))
                append(" -> ")
                append(getNum(place.id))
                append(";\n")
            }
        }
        append("}")
    }

    private fun decode(root: JsonElement): List<Coordinates>? = root.jsonObject["objects"]?.jsonArray?.mapNotNull {
        with(it.jsonObject) {
            val id = getUUID(this["name"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null)
            val pos = this["pos"]?.jsonPrimitive?.contentOrNull?.split(",")?.map { it.toDoubleOrNull() }
                ?: return@mapNotNull null
            if (pos.size != 2) return@mapNotNull null
            val x = pos[0] ?: return@mapNotNull null
            val y = pos[1] ?: return@mapNotNull null
            Coordinates(id, x, y)
        }
    }

    private fun executeDot(dotCode: String) = with(ProcessBuilder("dot", "-Tjson").start()) {
        outputStream.write(dotCode.encodeToByteArray())
        outputStream.close()
        val json = inputStream.readAllBytes().decodeToString()
        waitFor()
        Json.parseToJsonElement(json)
    }

    fun compute(): Layout {
        id2num.clear()
        val dotCode = generateDotCode()
        val element = executeDot(dotCode)
        return Layout(decode(element).orEmpty())
    }
}

fun PetriNet.computeLayout() = Layouter(this).compute()