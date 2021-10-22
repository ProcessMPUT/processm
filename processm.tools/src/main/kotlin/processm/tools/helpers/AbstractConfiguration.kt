package processm.tools.helpers

import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * A base class for configurations enabling automatic configuration from system properties (e.g., from environment)
 */
abstract class AbstractConfiguration {
    protected fun initFromEnvironment(prefix: String) {
        this::class.memberProperties.filterIsInstance<KMutableProperty<*>>().forEach { property ->
            val configurationPropertyName = "$prefix.${property.name}"
            val value = System.getProperty(configurationPropertyName)
            if (value !== null) {
                property.setter.isAccessible = true
                when (property.returnType) {
                    Int::class.createType() -> property.setter.call(this, value.toInt())
                    Long::class.createType() -> property.setter.call(this, value.toLong())
                    Double::class.createType() -> property.setter.call(this, value.toDouble())
                    String::class.createType() -> property.setter.call(this, value)
                    else -> TODO("${property.returnType} is not supported")
                }
            }
        }
    }
}