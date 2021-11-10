package processm.tools.generator

import processm.tools.helpers.AbstractConfiguration


/**
 * A configuration for [WWICompany], automatically initialized using JVM properties prefixed with `processm.tools.generator`
 */
class Configuration : AbstractConfiguration() {

    /**
     * Number of customer orders being processed in parallel
     */
    var nCustomerOrders = 10
        private set

    /**
     * Maximal number of concurrent connections to the database
     * */
    var connectionPoolSize = 2
        private set

    /**
     * How many times faster the clock in the simulation ticks, compared to the real world
     */
    var clockSpeedMultiplier = 3600L
        private set

    /**
     * How many times faster the clock in the purchase department ticks during the termination, compared to the clock in
     * the customer's order department
     *
     * This is to decrease the termination time. The clock in the purchase department will then tick
     * [purchaseDepartmentAdditionalClockSpeedMultiplierDuringTermination]*[clockSpeedMultiplier] times faster than the
     * real-world clock
     */
    var purchaseDepartmentAdditionalClockSpeedMultiplierDuringTermination = 1000L
        private set

    /**
     * JDBC URL to connect to the database
     */
    lateinit var dbURL: String
        private set

    /**
     * The database user
     */
    lateinit var dbUser: String
        private set

    /**
     * The password for the database user
     */
    lateinit var dbPassword: String
        private set

    /**
     * The lower bound (in ms) of one time unit in [WWICustomerOrderBusinessCase].
     *
     * The actual length of a time unit is randomized from [customerOrderMinStepLength]..[customerOrderMaxStepLength]
     */
    var customerOrderMinStepLength = 500L
        private set

    /**
     * The upper bound (in ms) of one time unit in [WWICustomerOrderBusinessCase]
     *
     * The actual length of a time unit is randomized from [customerOrderMinStepLength]..[customerOrderMaxStepLength]
     */
    var customerOrderMaxStepLength = 1000L
        private set

    /**
     * The lower bound (in ms) of one time unit in [WWIPurchaseOrderBusinessCase]
     *
     * The actual length of a time unit is randomized from [purchaseOrderMinStepLength]..[purchaseOrderMaxStepLength]
     */
    var purchaseOrderMinStepLength = 5000L
        private set

    /**
     * The upper bound (in ms) of one time unit in [WWIPurchaseOrderBusinessCase]
     *
     * The actual length of a time unit is randomized from [purchaseOrderMinStepLength]..[purchaseOrderMaxStepLength]
     */
    var purchaseOrderMaxStepLength = 10000L
        private set

    /**
     * Delay in ms between checking whether new purchasing orders should be placed
     */
    var delayBetweenPlacingPurchaseOrders = 10000L
        private set

    /**
     * A random seed to ensure reproducibility. Not always used directly.
     */
    var randomSeed = 0xDECAFBADL
        private set

    /**
     * A random seed to ensure reproducibility. Used indirectly by [WWICustomerOrderBusinessCase].
     */
    var customerOrderRandomSeedMultiplier = 0xC0FFEEL
        private set

    /**
     * A random seed to ensure reproducibility. Used indirectly by [WWIPurchaseOrderBusinessCase].
     */
    var purchaseOrderRandomSeedMultiplier = 0xBEEF600DL
        private set

    /**
     * A probability that the delivery in the final steps of [WWICustomerOrderBusinessCase] will succeed.
     */
    var customerOrderBusinessCaseSuccessfulDeliveryProbabilty = 0.5
        private set

    /**
     * A probability that the customer pays before the delivery in [WWICustomerOrderBusinessCase]
     */
    var customerOrderBusinessCasePaymentBeforeDeliveryProbability = 0.5
        private set

    /**
     * Maximal delay (in time units) between consecutive steps in [WWICustomerOrderBusinessCase]
     */
    var customerOrderBusinessCaseMaxDelay = 10
        private set

    /**
     * A probability that some lines will be added to an order during the pickup phase in [WWICustomerOrderBusinessCase]
     */
    var customerOrderBusinessCaseAddLinesProbability = 0.1
        private set

    /**
     * A probability that some lines will be removed from an order during the pickup phase in [WWICustomerOrderBusinessCase],
     * before they're picked up
     */
    var customerOrderBusinessCaseRemoveLineProbability = 0.0
        private set

    // init must be the last in the class body to ensure all the properties were already initialized
    init {
        val prefix = "processm.tools.generator"
        initFromEnvironment(prefix)
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