package processm.tools.generator

import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.createType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.isAccessible


object Configuration {

    var nCustomerOrders = 10
        private set
    var connectionPoolSize = 2
        private set
    var clockSpeedMultiplier = 3600L
        private set
    lateinit var dbURL: String
        private set
    lateinit var dbUser: String
        private set
    lateinit var dbPassword: String
        private set
    var customerOrderMinStepLength = 500L
        private set
    var customerOrderMaxStepLength = 1000L
        private set
    var purchaseOrderMinStepLength = 5000L
        private set
    var purchaseOrderMaxStepLength = 10000L
        private set
    var delayBetweenPlacingPurchaseOrders = 10000L
        private set
    var randomSeed = 0xDECAFBADL
        private set
    var customerOrderRandomSeedMultiplier = 0xC0FFEEL
        private set
    var purchaseOrderRandomSeedMultiplier = 0xBEEF600DL
        private set
    var customerOrderBusinessCaseSuccessfulDeliveryProbabilty = 0.5
        private set
    var customerOrderBusinessCasePaymentBeforeDeliveryProbability = 0.5
        private set
    var customerOrderBusinessCaseMaxDelay = 10
        private set
    var customerOrderBusinessCaseAddLinesProbability = 0.1
        private set
    var customerOrderBusinessCaseRemoveLineProbability = 0.0
        private set

    // init must be the last in the class body to ensure all the properties were already initialized
    init {
        val prefix = "processm.tools.generator"
        this::class.memberProperties.filterIsInstance<KMutableProperty<*>>().forEach { property ->
            val configurationPropertyName = "$prefix.${property.name}"
            val value = System.getProperty(configurationPropertyName)
            if (value !== null) {
                property.setter.isAccessible = true
                when (property.returnType) {
                    Int::class.createType() -> property.setter.call(value.toInt())
                    Long::class.createType() -> property.setter.call(value.toLong())
                    String::class.createType() -> property.setter.call(value)
                    else -> TODO("${property.returnType} is not supported")
                }
            }
        }
        require(nCustomerOrders >= 1)
        require(connectionPoolSize >= 1)
        require(clockSpeedMultiplier >= 1)
        require(customerOrderMinStepLength <= customerOrderMaxStepLength)
        require(purchaseOrderMinStepLength <= purchaseOrderMaxStepLength)
        require(customerOrderBusinessCaseSuccessfulDeliveryProbabilty in 0.0..1.0)
        require(customerOrderBusinessCasePaymentBeforeDeliveryProbability in 0.0..1.0)
        require(customerOrderBusinessCaseMaxDelay >= 1)
        require(customerOrderBusinessCaseAddLinesProbability in 0.0..1.0)
        require(customerOrderBusinessCaseRemoveLineProbability in 0.0..1.0)
        require(customerOrderBusinessCaseAddLinesProbability + customerOrderBusinessCaseRemoveLineProbability < 1.0)
        require(this::dbURL.isInitialized) { "$prefix.dbURL must be set" }
        require(this::dbUser.isInitialized) { "$prefix.dbUser must be set" }
        require(this::dbPassword.isInitialized) { "$prefix.dbPassword must be set" }
    }
}