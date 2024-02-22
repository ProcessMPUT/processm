package processm.experimental.services.gson

import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import processm.logging.debug
import processm.logging.loggedScope
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties

/**
 * A [TypeAdapterFactory] for Gson that prevents putting null values into non-nullable properties in kotlin.
 */
class NonNullableTypeAdapterFactory : TypeAdapterFactory {
    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? = loggedScope { logger ->
        val delegate = gson.getDelegateAdapter(this, type)

        // If the class isn't kotlin, don't use the custom type adapter
        if (type.rawType.declaredAnnotations.none { it.annotationClass.qualifiedName == "kotlin.Metadata" }) {
            return null
        }

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter, value: T?) = delegate.write(out, value)
            override fun read(input: JsonReader): T? {
                val value: T? = delegate.read(input)
                if (value != null) {
                    val kotlinClass: KClass<Any> = Reflection.createKotlinClass(type.rawType)
                    // Ensure none of its non-nullable fields were deserialized to null
                    kotlinClass.memberProperties.forEach {
                        if (!it.returnType.isMarkedNullable && it.get(value) == null) {
                            logger.debug { "Value of the non-nullable member ${kotlinClass.simpleName}::${it.name} must not be null" }
                            return null
                        }
                    }
                }
                return value
            }
        }
    }
}
