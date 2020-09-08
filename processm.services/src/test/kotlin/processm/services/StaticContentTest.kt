package processm.services

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContextBuilder
import java.net.URL
import kotlin.test.*

class StaticContentTest {
    // TODO: configure tests such that this one runs for both: filesystem and JAR-originated resources
    private val baseURI = URL("http://localhost:2080/")
    private val baseURIs = URL("https://localhost:2443/")
    private val host = WebServicesHost()

    init {
        host.register()
    }

    @BeforeTest
    fun setUp() {
        host.start()
    }

    @AfterTest
    fun cleanUp() {
        host.stop()
    }

    @Test
    fun getRootHTML() = runBlocking {
        val client = HttpClient(Apache) {
            engine {
                customizeClient {
                    setSSLContext(
                        SSLContextBuilder.create().loadTrustMaterial(TrustSelfSignedStrategy()).build()
                    )
                    setSSLHostnameVerifier(NoopHostnameVerifier())
                    connectTimeout = 1000
                    connectionRequestTimeout = 1000
                    socketTimeout = 1000
                }
            }
        }
        val httpGetDoc = client.get<String>(baseURI)
        val httpsGetDoc = client.get<String>(baseURIs)

        assertTrue(httpGetDoc.startsWith("<!DOCTYPE html>"))
        assertEquals(httpGetDoc, httpsGetDoc)
    }
}
