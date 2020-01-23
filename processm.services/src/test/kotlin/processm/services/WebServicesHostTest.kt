package processm.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContextBuilder
import org.junit.Test
import java.net.URL
import kotlin.test.assertFails
import kotlin.test.assertTrue

class WebServicesHostTest {

    private val baseURIs = URL("https://localhost:2443/")
    val host = WebServicesHost
    val client = HttpClient(Apache) {
        engine {
            customizeClient {
                setSSLContext(
                    SSLContextBuilder
                        .create()
                        .loadTrustMaterial(TrustSelfSignedStrategy())
                        .build()
                )
                setSSLHostnameVerifier(NoopHostnameVerifier())
                connectTimeout = 1000
                connectionRequestTimeout = 1000
                socketTimeout = 1000
            }
        }
    }

    init {
        host.register()
    }

    @Test
    fun StartStopStartStop() = runBlocking {
        for (i in 0..2) {
            host.start()

            var response = client.get<String>(baseURIs)
            assertTrue(response.startsWith("<!DOCTYPE html>"))

            host.stop()
            assertFails {
                response = client.get<String>(baseURIs)
            }
        }
    }

}