package processm.helpers.serialization

import kotlinx.serialization.modules.SerializersModule

/**
 * A serializers module provider for configuring kotlinx/serialization serializer.
 * This interface is intended for use as a Java service loaded using [java.util.ServiceLoader] class.
 */
interface SerializersModuleProvider {
    fun getSerializersModule(): SerializersModule
}
