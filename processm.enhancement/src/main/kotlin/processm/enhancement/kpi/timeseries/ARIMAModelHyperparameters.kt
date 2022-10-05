package processm.enhancement.kpi.timeseries

/**
 * @param p The autoregressive order
 * @param d The differentiation order
 * @param q The moving average order
 */
data class ARIMAModelHyperparameters(val p: Int, val d: Int, val q: Int) {
    /**
     * A heuristic measure of model complexity. The idea is: the lower the orders, the better the model, as there are
     * fewer parameters to estimate and store.
     */
    internal val complexity: Int
        get() = p + d + q
}