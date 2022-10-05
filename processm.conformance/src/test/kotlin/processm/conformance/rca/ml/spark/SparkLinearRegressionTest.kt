package processm.conformance.rca.ml.spark

import processm.core.helpers.cartesianProduct
import processm.core.log.Helpers.assertDoubleEquals
import kotlin.test.Test
import kotlin.test.assertEquals

class SparkLinearRegressionTest {

    @Test
    fun `y=10x1+2x2+5`() {
        val X = listOf(arrayListOf(1.0, 2.0, 3.0, 4.0, 5.0), arrayListOf(1.0, 2.0, 3.0, 4.0, 5.0)).cartesianProduct()
            .toList()
        val ds = X.map { x -> x to (10 * x[0] + 2 * x[1] + 5) }
        with(SparkLinearRegression().fit(ds)) {
            assertEquals(2, coefficients.size)
            assertDoubleEquals(10.0, coefficients[0])
            assertDoubleEquals(2.0, coefficients[1])
            assertDoubleEquals(5.0, intercept)
        }
    }
}