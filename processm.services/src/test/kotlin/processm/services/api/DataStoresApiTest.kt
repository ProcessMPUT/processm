package processm.services.api

import io.ktor.http.*
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import org.junit.jupiter.api.TestInstance
import org.koin.test.mock.declareMock
import processm.helpers.time.toLocalDateTime
import processm.services.api.models.*
import processm.services.logic.DataStoreService
import processm.services.logic.LogsService
import java.time.Instant
import java.util.*
import java.util.stream.Stream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataStoresApiTest : BaseApiTest() {
    //val organizationService: OrganizationService by lazy { declareMock() }
    val dataStoreService: DataStoreService by lazy { declareMock() }
    val logsService: LogsService by lazy { declareMock() }

    override fun endpointsWithAuthentication(): Stream<Pair<HttpMethod, String>?> = Stream.of(null)

    override fun endpointsWithNoImplementation(): Stream<Pair<HttpMethod, String>?> = Stream.of(null)

    @Test
    fun `complete workflow for testing ETL process`() =
        withConfiguredTestApplication {
            val dataStoreId = UUID.randomUUID()
            val dataConnectorId = UUID.randomUUID()
            val organizationId = UUID.randomUUID()
            val userId = UUID.randomUUID()
            val etlProcessId = UUID.randomUUID()
            val logIdentityId = UUID.randomUUID()
            val cfg = JdbcEtlProcessConfiguration(
                query = "",
                enabled = false,
                batch = false,
                traceId = JdbcEtlColumnConfiguration("trace", "trace"),
                eventId = JdbcEtlColumnConfiguration("event", "event"),
                attributes = emptyArray(),
            )
            val process = AbstractEtlProcess(
                name = "name",
                dataConnectorId = dataConnectorId,
                type = EtlProcessType.jdbc,
                configuration = cfg
            )

            val text = """[{"foo": "bar"}]"""
            val lastExecutionTime = Instant.now()

            withAuthentication(userId, role = OrganizationRole.owner to organizationId) {
                every {
                    dataStoreService.createSamplingJdbcEtlProcess(dataStoreId, dataConnectorId, any(), any(), any())
                } returns etlProcessId andThenThrows IllegalStateException()
                every {
                    dataStoreService.getEtlProcessInfo(dataStoreId, etlProcessId)
                } returns DataStoreService.EtlProcessInfo(logIdentityId, emptyList(), lastExecutionTime)
                every {
                    logsService.queryDataStoreJSON(dataStoreId, "where log:identity:id=$logIdentityId", true, true)
                } returns {
                    this.write(text.toByteArray())
                }
                every {
                    logsService.removeLog(dataStoreId, logIdentityId)
                } just Runs
                every {
                    dataStoreService.removeEtlProcess(dataStoreId, etlProcessId)
                } just Runs
                every {
                    dataStoreService.assertUserHasSufficientPermissionToDataStore(
                        userId,
                        dataStoreId,
                        any()
                    )
                } just Runs
                with(
                    handleRequest(
                        HttpMethod.Post,
                        "/api/data-stores/$dataStoreId/sampling-etl-processes/"
                    ) {
                        withSerializedBody(process)
                    }
                ) {
                    assertEquals(HttpStatusCode.Created, response.status())
                    val cadaver = assertNotNull(response.deserializeContent<AbstractEtlProcess>())
                    println(cadaver)

                    assertEquals(etlProcessId, cadaver.id)
                    assertEquals(dataConnectorId, cadaver.dataConnectorId)
                    assertEquals(EtlProcessType.jdbc, cadaver.type)
                }
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/api/data-stores/$dataStoreId/etl-processes/$etlProcessId"
                    )
                ) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val cadaver = assertNotNull(response.deserializeContent<EtlProcessInfo>())
                    assertEquals(logIdentityId, cadaver.logIdentityId)
                    assertEquals(lastExecutionTime.toLocalDateTime(), cadaver.lastExecutionTime)
                }
                with(
                    handleRequest(
                        HttpMethod.Get,
                        "/api/data-stores/$dataStoreId/logs?query=where+log:identity:id=$logIdentityId"
                    )
                ) {
                    assertEquals(HttpStatusCode.OK, response.status())
                    val cadaver = assertNotNull(response.deserializeContent<List<Any>>())
                    assertEquals(1, cadaver.size)
                    val map = cadaver[0]
                    assertIs<Map<String, String>>(map)
                    assertEquals(1, map.size)
                    assertEquals("bar", map["foo"])
                }
                with(handleRequest(HttpMethod.Delete, "/api/data-stores/$dataStoreId/logs/$logIdentityId")) {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                }
                with(
                    handleRequest(
                        HttpMethod.Delete,
                        "/api/data-stores/$dataStoreId/etl-processes/$etlProcessId"
                    )
                ) {
                    assertEquals(HttpStatusCode.NoContent, response.status())
                }
            }
        }
}
