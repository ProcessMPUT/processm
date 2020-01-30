package processm.core.helpers

import java.util.*

private const val EnvironmentVariablePrefix = "processm_"
private class Dummy{}

fun loadConfiguration() {
    // Load from environment variables processm_* by replacing _ with .
    System.getenv().filterKeys { it.startsWith(EnvironmentVariablePrefix, true) }.forEach {
        System.setProperty(it.key.replace("_", "."), it.value)
    }

    // Load from configuration file, possibly overriding the environment settings
    Dummy::class.java.classLoader.getResourceAsStream("config.properties").use {
        Properties().apply { load(it) }.forEach { System.setProperty(it.key as String, it.value as String) }
    }
}