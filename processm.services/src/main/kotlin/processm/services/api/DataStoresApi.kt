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
import processm.helpers.time.toLocalDateTime
import processm.services.api.models.*
import processm.services.helpers.ExceptionReason
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
    val dataStoreService by inject<DataStoreService>()
    val logsService by inject<LogsService>()

    authenticate {
        post<Paths.DataStores> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            val messageBody = kotlin.runCatching { call.receiveNullable<DataStore>() }.getOrNull()
                ?: throw ApiException(ExceptionReason.UnparsableData)

            if (messageBody.name.isEmpty()) throw ApiException(ExceptionReason.DataStoreNameRequired)
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
                    dataStoreSize,
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
                ?: throw ApiException(ExceptionReason.UnparsableData)
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
                } else throw ApiException(ExceptionReason.UnexpectedRequestParameter, arrayOf(part?.name))
            } catch (e: XMLStreamException) {
                throw ApiException(ExceptionReason.InvalidFile, arrayOf("XES"), message = e.message)
            } catch (e: ZipException) {
                throw ApiException(ExceptionReason.InvalidFile, arrayOf("ZIP"), message = e.message)
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
            val includeEvents = call.parameters["includeEvents"]?.toBoolean() ?: true
            val includeTraces = includeEvents || (call.parameters["includeTraces"]?.toBoolean() ?: true)
            val accept = call.request.accept() ?: "application/json";
            val mime: ContentType
            val formatter: (uuid: UUID, query: String, includeTraces: Boolean, includeEvents: Boolean) -> OutputStream.() -> Unit
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

                else -> throw ApiException(ExceptionReason.UnsupportedContentType, arrayOf(accept))
            }

            try {
                val queryProcessor = formatter(pathParams.dataStoreId, query, includeTraces, includeEvents)
                call.respondOutputStream(mime, HttpStatusCode.OK) {
                    queryProcessor(this)
                }
            } catch (e: RecognitionException) {
                throw ApiException(ExceptionReason.PQLError, arrayOf(e.message))
            } catch (e: IllegalArgumentException) {
                throw ApiException(ExceptionReason.PQLError, arrayOf(e.message))
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
            val dataConnectors = dataStoreService.getDataConnectors(pathParams.dataStoreId)

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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            val connectorProperties =
                dataConnector.connectionProperties ?: throw ApiException(ExceptionReason.ConnectorConfigurationRequired)
            val connectorName =
                dataConnector.name ?: throw ApiException(ExceptionReason.ConnectorNameRequired)
            val dataConnectorId =
                dataStoreService.createDataConnector(pathParams.dataStoreId, connectorName, connectorProperties)

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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            dataStoreService.updateDataConnector(
                pathParams.dataStoreId,
                pathParams.dataConnectorId,
                dataConnector.name,
                dataConnector.connectionProperties
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
            val connectionProperties =
                runCatching { call.receiveNullable<DataConnector>() }.getOrNull()?.connectionProperties
                    ?: throw ApiException(ExceptionReason.UnparsableData)

            try {
                dataStoreService.testDatabaseConnection(connectionProperties)
            } catch (e: Exception) {
                throw ApiException(ExceptionReason.ConnectionTestFailed, arrayOf(e.message))
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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            if (etlProcessData.dataConnectorId == null) throw ApiException(ExceptionReason.ConnectorReferenceRequired)
            if (etlProcessData.name.isNullOrBlank()) throw ApiException(ExceptionReason.ETLProcessNameRequired)

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
                            ?: throw ApiException(ExceptionReason.EmptyETLConfigurationNotSupported)
                    dataStoreService.saveJdbcEtlProcess(
                        null,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException(ExceptionReason.ETLProcessTypeNotSupported)
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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            if (etlProcessData.isActive == null) throw ApiException(ExceptionReason.ActivationStatusRequired)
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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            if (etlProcessData.dataConnectorId == null) throw ApiException(ExceptionReason.ConnectorReferenceRequired)
            if (etlProcessData.name.isNullOrBlank()) throw ApiException(ExceptionReason.ETLProcessNameRequired)

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
                            ?: throw ApiException(ExceptionReason.EmptyETLConfigurationNotSupported)
                    dataStoreService.saveJdbcEtlProcess(
                        etlProcessData.id,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException(ExceptionReason.ETLProcessTypeNotSupported)
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
                throw ApiException(ExceptionReason.NotFound)
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
                ?: throw ApiException(ExceptionReason.UnparsableData)
            val id = when (etlProcessData.type) {
                EtlProcessType.jdbc -> {
                    etlProcessData.configuration
                        ?: throw ApiException(ExceptionReason.EmptyETLConfigurationNotSupported)
                    etlProcessData.dataConnectorId
                        ?: throw ApiException(ExceptionReason.ConnectorReferenceRequired)
                    etlProcessData.name ?: throw ApiException(ExceptionReason.ETLProcessNameRequired)
                    dataStoreService.createSamplingJdbcEtlProcess(
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        etlProcessData.configuration,
                        nComponents
                    )
                }

                else -> throw ApiException(ExceptionReason.ETLProcessTypeNotSupported)
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
