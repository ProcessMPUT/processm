package processm.services

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContextBuilder
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertFails
import kotlin.test.assertTrue

class WebServicesHostTest {
    private val baseURIs = URL("https://localhost:2443/")
    private val host = WebServicesHost()
    private val client = HttpClient(Apache) {
        engine {
            customizeClient {
                setSSLContext(SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy()).build())
                setSSLHostnameVerifier(NoopHostnameVerifier())
                connectTimeout = 5_000
                connectionRequestTimeout = 10_000
                socketTimeout = 5_000
            }
        }
    }

    init {
        host.register()
    }

    @Test
    fun startStopStartStopTest() = runBlocking {
        for (i in 0..2) {
            try {
                host.start()
                val response = client.get(baseURIs).bodyAsText()
                assertTrue(response.startsWith("<!DOCTYPE html>", ignoreCase = true))
            } finally {
                host.stop()
            }

            assertFails {
                val response = client.get(baseURIs).bodyAsText()
            }
        }
    }
}
