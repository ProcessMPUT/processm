package processm.services.api


import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import org.antlr.v4.runtime.RecognitionException
import org.koin.ktor.ext.inject
import processm.dbmodels.models.RoleType
import processm.helpers.mapToArray
import processm.helpers.toLocalDateTime
import processm.services.api.models.*
import processm.services.logic.DataStoreService
import processm.services.logic.LogsService
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipException
import javax.xml.stream.XMLStreamException


//TODO refactor as a user-exposed configuration
const val defaultSampleSize = 100
const val maxSampleSize = 200

@KtorExperimentalLocationsAPI
fun Route.DataStoresApi() {
    val connectionStringPropertyName = "connection-string"
    val dataStoreService by inject<DataStoreService>()
    val logsService by inject<LogsService>()

    authenticate {
        post<Paths.DataStores> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            val messageBody = kotlin.runCatching { call.receiveNullable<DataStore>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)

            if (messageBody.name.isEmpty()) throw ApiException(ApiExceptionReason.NAME_FOR_DATA_STORE_IS_REQUIRED)
            val ds =
                dataStoreService.createDataStore(
                    userId = principal.userId,
                    name = messageBody.name
                )
            call.respond(HttpStatusCode.Created, DataStore(name = ds.name, id = ds.id.value))
        }

        get<Paths.DataStores> {
            val principal = call.authentication.principal<ApiUser>()!!
            val dataStores = dataStoreService.getUserDataStores(principal.userId).map {
                DataStore(it.name, it.id.value, null, it.creationDate)
            }.toTypedArray()

            call.respond(HttpStatusCode.OK, dataStores)
        }

        get<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val dataStoreSize = dataStoreService.getDatabaseSize(pathParams.dataStoreId.toString())
            val dataStore = dataStoreService.getDataStore(pathParams.dataStoreId)

            call.respond(
                HttpStatusCode.OK,
                DataStore(
                    dataStore.name,
                    dataStore.id.value,
                    dataStoreSize.toInt(),
                    dataStore.creationDate
                )
            )
        }

        delete<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeDataStore(pathParams.dataStoreId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            val dataStore = kotlin.runCatching { call.receiveNullable<DataStore>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            dataStoreService.renameDataStore(pathParams.dataStoreId, dataStore.name)

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            try {
                val part = call.receiveMultipart().readPart()

                if (part is PartData.FileItem) {
                    part.streamProvider().use { requestStream ->
                        logsService.saveLogFile(pathParams.dataStoreId, part.originalFileName, requestStream)
                    }
                } else throw ApiException(ApiExceptionReason.UNEXPECTED_REQUEST_PARAMETER, arrayOf(part?.name))
            } catch (e: XMLStreamException) {
                throw ApiException(ApiExceptionReason.NOT_A_VALID_FILE, arrayOf("XES"), message = e.message)
            } catch (e: ZipException) {
                throw ApiException(ApiExceptionReason.NOT_A_VALID_FILE, arrayOf("ZIP"), message = e.message)
            }

            call.respond(HttpStatusCode.Created)
        }

        get<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val query = call.parameters["query"] ?: ""
            val accept = call.request.accept() ?: "application/json";
            val mime: ContentType
            val formatter: (uuid: UUID, query: String) -> OutputStream.() -> Unit
            when (accept.split(',').first { it == "application/json" || it == "application/zip" }) {
                "application/json" -> {
                    mime = ContentType.Application.Json
                    formatter = logsService::queryDataStoreJSON
                }

                "application/zip" -> {
                    mime = ContentType.Application.Zip
                    formatter = logsService::queryDataStoreZIPXES
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "xes.zip")
                            .toString()
                    )
                }

                else -> throw ApiException(ApiExceptionReason.UNSUPPORTED_CONTENT_TYPE, arrayOf(accept))
            }

            try {
                val queryProcessor = formatter(pathParams.dataStoreId, query)
                call.respondOutputStream(mime, HttpStatusCode.OK) {
                    queryProcessor(this)
                }
            } catch (e: RecognitionException) {
                throw ApiException(ApiExceptionReason.PQL_ERROR, arrayOf(e.message))
            } catch (e: IllegalArgumentException) {
                throw ApiException(ApiExceptionReason.PQL_ERROR, arrayOf(e.message))
            }
        }

        delete<Paths.Log> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            logsService.removeLog(pathParams.dataStoreId, pathParams.identityId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.DataConnectors> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val dataConnectors = dataStoreService.getDataConnectors(pathParams.dataStoreId).mapToArray {
                DataConnector(
                    it.id,
                    it.name,
                    it.lastConnectionStatus,
                    it.lastConnectionStatusTimestamp?.toString(),
                    it.connectionProperties
                )
            }

            call.respond(HttpStatusCode.OK, dataConnectors)
        }

        post<Paths.DataConnectors> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val dataConnector = runCatching { call.receiveNullable<DataConnector>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            val connectorProperties =
                dataConnector.properties ?: throw ApiException(ApiExceptionReason.CONNECTOR_CONFIGURATION_REQUIRED)
            val connectorName =
                dataConnector.name ?: throw ApiException(ApiExceptionReason.NAME_FOR_DATA_CONNECTOR_IS_REQUIRED)
            val connectionString = connectorProperties[connectionStringPropertyName]
            val dataConnectorId =
                if (connectionString.isNullOrBlank()) dataStoreService.createDataConnector(
                    pathParams.dataStoreId,
                    connectorName,
                    connectorProperties
                )
                else dataStoreService.createDataConnector(pathParams.dataStoreId, connectorName, connectionString)

            call.respond(
                HttpStatusCode.Created,
                DataConnector(
                    dataConnectorId,
                    connectorName,
                    lastConnectionStatus = null,
                    lastConnectionStatusTimestamp = null
                )
            )
        }

        delete<Paths.DataConnector> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeDataConnector(pathParams.dataStoreId, pathParams.dataConnectorId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataConnector> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val dataConnector = kotlin.runCatching { call.receiveNullable<DataConnector>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            dataStoreService.renameDataConnector(
                pathParams.dataStoreId,
                pathParams.dataConnectorId,
                dataConnector.name ?: throw ApiException(ApiExceptionReason.NAME_FOR_DATA_CONNECTOR_IS_REQUIRED)
            )

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.ConnectionTest> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val connectionProperties = runCatching { call.receiveNullable<DataConnector>() }.getOrNull()?.properties
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            val connectionString = connectionProperties[connectionStringPropertyName]

            try {
                if (connectionString.isNullOrBlank()) dataStoreService.testDatabaseConnection(connectionProperties)
                else dataStoreService.testDatabaseConnection(connectionString)
            } catch (e: Exception) {
                throw ApiException(ApiExceptionReason.CONNECTION_TEST_FAILED, arrayOf(e.message))
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.CaseNotionSuggestions> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val caseNotionSuggestions =
                dataStoreService.getCaseNotionSuggestions(pathParams.dataStoreId, pathParams.dataConnectorId)
                    .toTypedArray()

            call.respond(
                HttpStatusCode.OK,
                caseNotionSuggestions
            )
        }

        get<Paths.RelationshipGraph> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val relationshipGraph =
                dataStoreService.getRelationshipGraph(pathParams.dataStoreId, pathParams.dataConnectorId)

            call.respond(
                HttpStatusCode.OK,
                relationshipGraph
            )
        }

        get<Paths.EtlProcesses> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )

            val etlProcesses = dataStoreService.getEtlProcesses(pathParams.dataStoreId)

            call.respond(
                HttpStatusCode.OK,
                etlProcesses
            )
        }

        post<Paths.EtlProcesses> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val etlProcessData = runCatching { call.receiveNullable<AbstractEtlProcess>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            if (etlProcessData.dataConnectorId == null) throw ApiException(ApiExceptionReason.DATA_CONNECTOR_REFERENCE_IS_REQUIRED)
            if (etlProcessData.name.isNullOrBlank()) throw ApiException(ApiExceptionReason.NAME_FOR_ETL_PROCESS_IS_REQUIRED)

            val etlProcessId = when (etlProcessData.type) {
                EtlProcessType.automatic -> {
                    val relations = etlProcessData.caseNotion?.edges.orEmpty().toList()
                    dataStoreService.saveAutomaticEtlProcess(
                        null,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        relations
                    )
                }

                EtlProcessType.jdbc -> {
                    val configuration =
                        etlProcessData.configuration
                            ?: throw ApiException(ApiExceptionReason.EMPTY_ETL_CONFIGURATION_NOT_SUPPORTED)
                    dataStoreService.saveJdbcEtlProcess(
                        null,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException(ApiExceptionReason.ETL_PROCESS_TYPE_NOT_SUPPORTED)
            }

            call.respond(
                HttpStatusCode.Created,
                AbstractEtlProcess(
                    etlProcessId,
                    etlProcessData.name,
                    etlProcessData.dataConnectorId,
                    type = etlProcessData.type
                )
            )
        }

        patch<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val etlProcessData = runCatching { call.receiveNullable<AbstractEtlProcess>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            if (etlProcessData.isActive == null) throw ApiException(ApiExceptionReason.ACTIVATION_STATUS_IS_REQUIRED)
            dataStoreService.changeEtlProcessActivationState(
                pathParams.dataStoreId,
                pathParams.etlProcessId,
                etlProcessData.isActive
            )

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.EtlProcess> { path ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                path.dataStoreId,
                RoleType.Reader
            )
            dataStoreService.triggerEtlProcess(path.dataStoreId, path.etlProcessId)
            call.respond(HttpStatusCode.NoContent)
        }

        put<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Writer
            )

            val etlProcessData = runCatching { call.receiveNullable<AbstractEtlProcess>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            if (etlProcessData.dataConnectorId == null) throw ApiException(ApiExceptionReason.DATA_CONNECTOR_REFERENCE_IS_REQUIRED)
            if (etlProcessData.name.isNullOrBlank()) throw ApiException(ApiExceptionReason.NAME_FOR_ETL_PROCESS_IS_REQUIRED)

            val etlProcessId = when (etlProcessData.type) {
                EtlProcessType.automatic -> {
                    val relations = etlProcessData.caseNotion?.edges.orEmpty().toList()
                    dataStoreService.saveAutomaticEtlProcess(
                        etlProcessData.id,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        relations
                    )
                }

                EtlProcessType.jdbc -> {
                    val configuration =
                        etlProcessData.configuration
                            ?: throw ApiException(ApiExceptionReason.EMPTY_ETL_CONFIGURATION_NOT_SUPPORTED)
                    dataStoreService.saveJdbcEtlProcess(
                        etlProcessData.id,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException(ApiExceptionReason.ETL_PROCESS_TYPE_NOT_SUPPORTED)
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            try {
                val info = dataStoreService.getEtlProcessInfo(pathParams.dataStoreId, pathParams.etlProcessId)
                val message = EtlProcessInfo(
                    info.logIdentityId,
                    info.errors.mapToArray { EtlError(it.message, it.time.toLocalDateTime(), it.exception) },
                    info.lastExecutionTime?.toLocalDateTime()
                )
                call.respond(HttpStatusCode.OK, message)
            } catch (_: NoSuchElementException) {
                throw ApiException(ApiExceptionReason.NOT_FOUND, responseCode = HttpStatusCode.NotFound)
            }
        }

        delete<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeEtlProcess(pathParams.dataStoreId, pathParams.etlProcessId)

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.SamplingEtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            val nComponents = (pathParams.nComponents ?: defaultSampleSize).coerceAtMost(maxSampleSize)
            val etlProcessData = runCatching { call.receiveNullable<AbstractEtlProcess>() }.getOrNull()
                ?: throw ApiException(ApiExceptionReason.UNPARSABLE_DATA)
            val id = when (etlProcessData.type) {
                EtlProcessType.jdbc -> {
                    etlProcessData.configuration ?: throw ApiException(ApiExceptionReason.EMPTY_ETL_CONFIGURATION_NOT_SUPPORTED)
                    etlProcessData.dataConnectorId ?: throw ApiException(ApiExceptionReason.DATA_CONNECTOR_REFERENCE_IS_REQUIRED)
                    etlProcessData.name ?: throw ApiException(ApiExceptionReason.NAME_FOR_ETL_PROCESS_IS_REQUIRED)
                    dataStoreService.createSamplingJdbcEtlProcess(
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        etlProcessData.configuration,
                        nComponents
                    )
                }

                else -> throw ApiException(ApiExceptionReason.ETL_PROCESS_TYPE_NOT_SUPPORTED)
            }

            call.respond(
                HttpStatusCode.Created,
                AbstractEtlProcess(
                    id,
                    etlProcessData.name,
                    etlProcessData.dataConnectorId,
                    etlProcessData.isActive,
                    etlProcessData.lastExecutionTime,
                    etlProcessData.type
                )
            )
        }
    }
}
