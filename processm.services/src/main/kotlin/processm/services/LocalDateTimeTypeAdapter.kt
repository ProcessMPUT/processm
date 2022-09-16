package processm.services

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.LocalDateTime

/**
 * A [TypeAdapter] for Gson for [LocalDateTime] class.
 */
class LocalDateTimeTypeAdapter : TypeAdapter<LocalDateTime>() {
    override fun write(out: JsonWriter, value: LocalDateTime?) {
        out.value(value?.toString())
    }

    override fun read(`in`: JsonReader): LocalDateTime = LocalDateTime.parse(`in`.nextString())
}
