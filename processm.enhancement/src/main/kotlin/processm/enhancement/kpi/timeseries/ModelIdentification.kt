package processm.enhancement.kpi.timeseries

import kotlin.math.*


/**
 * Splits [data] into two parts: training (consisting of the first [trainTestSplit]*`data.size` items) and test (the remainder),
 * then trains ARIMA models for all p in 0..[maxP], d in 0..[maxD] and q in 0..[maxQ] using the training part.
 * Then, for each model, makes a forecast of `training.size` elements, assuming that the random shocks/noise are 0.
 * Next, computes the mean-squared error (MSE) between the prediction and the test part.
 * Finds the lowest MSE and retains only those models that have MSE <= [slack]*lowest MSE.
 * If there is more than one such a model, the one with the lowest [ARIMAModelHyperparameters.complexity] is returned.
 *
 * This is not a typical method for identifying ARIMA models, but statisticians seems to be pretty chill about algorithmizing
 * it, offering helpful advices such as "Basically you just have to guess that one or two terms of each type may be
 * needed and then see what happens when you estimate the model." (https://online.stat.psu.edu/stat510/lesson/3/3.1)
 *
 * @param data The timeseries to model
 * @param maxP The maximal autoregressive order to consider
 * @param maxD The maximal differentiation order to consider
 * @param maxQ The maximal moving average order to consider. The current implementation of [ARIMA] is probably buggy
 * when it comes to handling moving averages, thus the default value of 0 is safe, yet skips a whole class of models.
 * @param trainTestSplit What part of [data] to keep as the training set. The default value is one of the rules of thumb
 * used in ML.
 * @param slack How many times worse MSE is still good enough.
 */
fun identifyARIMAModel(
    data: List<Double>,
    maxP: Int = 3,
    maxD: Int = 2,
    maxQ: Int = 0,
    trainTestSplit: Double = .8,
    slack: Double = 1.05
): ARIMAModelHyperparameters {
    require(slack >= 1.0)
    require(trainTestSplit in 0.0..1.0)
    require(data.size >= 2)
    val k = (trainTestSplit * data.size).toInt().coerceAtMost(data.size - 1)
    val training = data.subList(0, k)
    val test = data.subList(k, data.size)
    assert(training.isNotEmpty())
    assert(test.isNotEmpty())
    val models = ArrayList<Pair<Double, ARIMAModelHyperparameters>>()
    for (d in 0..maxD) {
        for (s in 0..maxP + maxQ) {
            for (q in max(0, s - maxP)..min(s, maxQ)) {
                val p = s - q
                val model = ARIMA(p, d, q).fit(training)
                val simulation = model.simulate().drop(training.size).take(test.size).toList()
                assert(test.size == simulation.size)
                val mse = (test zip simulation).sumOf { (x, y) -> (x - y).pow(2) } / test.size
                models.add(mse to ARIMAModelHyperparameters(p, d, q))
            }
        }
    }
    models.sortBy { it.first }
    models.forEach(::println)
    val mseThreshold = models.first().first * slack
    return models.filter { it.first <= mseThreshold }.maxWith { a, b ->
        val aComplexity = a.second.complexity
        val bComplexity = b.second.complexity
        if (aComplexity != bComplexity)
            bComplexity.compareTo(aComplexity)
        else
            a.first.compareTo(b.first)
    }.second
}