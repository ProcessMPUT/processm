package processm.selenium

import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestMethodOrder
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.TestWatcher

/**
 * A base class for tests where a single, long test case was split into multiple methods.
 * Ensures that if any test fails, all the remaining tests will not be executed.
 * The class should be used with [TestMethodOrder] to ensure tests are executed in a fixed, known order.
 */
@ExtendWith(TestCaseAsAClass.FailureWatcher::class)
abstract class TestCaseAsAClass {

    private var someTestFailed = false

    @BeforeEach
    fun abortAllIfAnythingFailed() {
        Assumptions.assumeFalse(someTestFailed)
    }

    class FailureWatcher : TestWatcher {
        override fun testAborted(context: ExtensionContext?, cause: Throwable?) {
            (context?.testInstance?.get() as TestCaseAsAClass?)?.someTestFailed = true
        }

        override fun testFailed(context: ExtensionContext?, cause: Throwable?) {
            (context?.testInstance?.get() as TestCaseAsAClass?)?.someTestFailed = true
        }
    }
}