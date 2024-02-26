package processm.core.esb

import processm.logging.logger
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertTrue

class HawtioTest {

    private val baseURL = URL("http://localhost:8080/hawtio/")
    private val hawtio = Hawtio()

    @Test
    fun httpGetTest() {
        try {
            hawtio.register()
            hawtio.start()

            val response = baseURL.readText()
            assertTrue(response.startsWith("<!DOCTYPE html>"))

        } catch (e: Throwable) {
            logger().error("", e)
            throw e
        } finally {
            hawtio.stop()
        }
    }

}
