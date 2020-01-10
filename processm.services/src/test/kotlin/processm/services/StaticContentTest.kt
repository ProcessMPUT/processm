package processm.services

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.request.get
import kotlinx.coroutines.runBlocking
import org.apache.http.conn.ssl.NoopHostnameVerifier
import org.apache.http.conn.ssl.TrustSelfSignedStrategy
import org.apache.http.ssl.SSLContextBuilder
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StaticContentTest {
    // TODO: configure tests such that this one runs for both: filesystem and JAR-originated resources
    private val baseURI = URL("http://localhost:80/")
    private val baseURIs = URL("https://localhost:443/")
    private val host = WebServicesHost(emptyArray())

    @Test
    fun GetRootHTML() = runBlocking {
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
                }
            }
        }

        val httpGetDoc = client.get<String>(baseURI)
        val httpsGetDoc = client.get<String>(baseURIs)

        assertTrue(httpGetDoc.startsWith("<!DOCTYPE html>"))
        assertEquals(httpGetDoc, httpsGetDoc)
    }
}
