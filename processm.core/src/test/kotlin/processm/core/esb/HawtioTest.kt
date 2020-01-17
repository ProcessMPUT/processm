package processm.core.esb

import java.net.URL
import kotlin.test.Test
import kotlin.test.assertTrue

class HawtioTest {

    private val baseURL = URL("http://localhost:8080/hawtio/")


    @Test
    fun httpGetTest() {
        try {
            Hawtio.register()
            Hawtio.start()

            val response = baseURL.readText()
            assertTrue(response.startsWith("<!DOCTYPE html>"))

        } finally {
            Hawtio.stop()
        }
    }

}