package processm.core.helpers

import java.util.*
import processm.core.logging.*

private const val EnvironmentVariablePrefix = "processm_"

private object Helpers {}

fun loadConfiguration() {
    // Load from environment variables processm_* by replacing _ with .
    System.getenv().filterKeys { it.startsWith(EnvironmentVariablePrefix, true) }.forEach {
        val key = it.key.replace("_", ".")
        Helpers.logger().debug("Setting parameter $key=${it.value} using environment variable ${it.key}")
        System.setProperty(key, it.value)
    }

    // Load from configuration file, possibly overriding the environment settings
    Helpers::class.java.classLoader.getResourceAsStream("config.properties").use {
        Properties().apply { load(it) }.forEach {
            Helpers.logger().debug("Setting parameter ${it.key}=${it.value} using config.properties")
            System.setProperty(it.key as String, it.value as String)
        }
    }
}