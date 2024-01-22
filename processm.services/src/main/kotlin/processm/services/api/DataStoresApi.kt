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
import processm.core.helpers.mapToArray
import processm.core.helpers.toLocalDateTime
import processm.dbmodels.models.RoleType
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
            val messageBody = call.receiveOrNull<DataStore>()
                ?: throw ApiException("The provided data store data cannot be parsed")

            principal.ensureUserBelongsToOrganization(pathParams.organizationId)

            if (messageBody.name.isEmpty()) throw ApiException("Data store name needs to be specified")
            val ds =
                dataStoreService.createDataStore(
                    userId = principal.userId,
                    organizationId = pathParams.organizationId,
                    name = messageBody.name
                )
            call.respond(HttpStatusCode.Created, DataStore(name = ds.name, id = ds.id.value))
        }

        get<Paths.DataStores> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            val dataStores = dataStoreService.allByOrganizationId(organizationId = pathParams.organizationId).map {
                DataStore(it.name, it.id, null, it.creationDate)
            }.toTypedArray()

            call.respond(HttpStatusCode.OK, dataStores)
        }

        get<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val dataStoreSize = dataStoreService.getDatabaseSize(pathParams.dataStoreId.toString())
            val dataStore = dataStoreService.getDataStore(pathParams.dataStoreId)

            call.respond(
                HttpStatusCode.OK,
                DataStore(
                    dataStore.name,
                    dataStore.id,
                    dataStoreSize.toInt(),
                    dataStore.creationDate
                )
            )
        }

        delete<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeDataStore(pathParams.dataStoreId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            val dataStore = call.receiveOrNull<DataStore>()
                ?: throw ApiException("The provided data store data cannot be parsed")
            dataStoreService.renameDataStore(pathParams.dataStoreId, dataStore.name)

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            try {
                val part = call.receiveMultipart().readPart()

                if (part is PartData.FileItem) {
                    part.streamProvider().use { requestStream ->
                        logsService.saveLogFile(pathParams.dataStoreId, part.originalFileName, requestStream)
                    }
                } else throw ApiException("Unexpected request parameter: ${part?.name}")
            } catch (e: XMLStreamException) {
                throw ApiException("The file is not a valid XES file: ${e.message}")
            } catch (e: ZipException) {
                throw ApiException("The file is could not be decoded: ${e.message}")
            }

            call.respond(HttpStatusCode.Created)
        }

        get<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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

                else -> throw ApiException("Unsupported content-type: $accept.")
            }

            try {
                val queryProcessor = formatter(pathParams.dataStoreId, query)
                call.respondOutputStream(mime, HttpStatusCode.OK) {
                    queryProcessor(this)
                }
            } catch (e: RecognitionException) {
                throw ApiException(e.message)
            } catch (e: IllegalArgumentException) {
                throw ApiException(e.message)
            }
        }

        delete<Paths.Log> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            logsService.removeLog(pathParams.dataStoreId, pathParams.identityId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.DataConnectors> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val dataConnector = call.receiveOrNull<DataConnector>()
                ?: throw ApiException("The provided data connector configuration cannot be parsed")
            val connectorProperties =
                dataConnector.properties ?: throw ApiException("Connector configuration is required")
            val connectorName = dataConnector.name ?: throw ApiException("A name for data connector is required")
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeDataConnector(pathParams.dataStoreId, pathParams.dataConnectorId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataConnector> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val dataConnector = call.receiveOrNull<DataConnector>()
                ?: throw ApiException("The provided data connector data cannot be parsed")
            dataStoreService.renameDataConnector(
                pathParams.dataStoreId,
                pathParams.dataConnectorId,
                dataConnector.name ?: throw ApiException("A name for data connector is required")
            )

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.ConnectionTest> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Reader
            )
            val connectionProperties = call.receiveOrNull<DataConnector>()?.properties
                ?: throw ApiException("The provided data connector configuration cannot be parsed")
            val connectionString = connectionProperties[connectionStringPropertyName]

            try {
                if (connectionString.isNullOrBlank()) dataStoreService.testDatabaseConnection(connectionProperties)
                else dataStoreService.testDatabaseConnection(connectionString)
            } catch (e: Exception) {
                throw ApiException(e.message)
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.CaseNotionSuggestions> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val etlProcessData = call.receiveOrNull<AbstractEtlProcess>()
                ?: throw ApiException("The provided ETL process definition cannot be parsed")
            if (etlProcessData.dataConnectorId == null) throw ApiException("A data connector reference is required")
            if (etlProcessData.name.isNullOrBlank()) throw ApiException("A name for ETL process is required")

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
                        etlProcessData.configuration ?: throw ApiException("Empty ETL configuration is not supported")
                    dataStoreService.saveJdbcEtlProcess(
                        null,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException("The provided ETL process type is not supported")
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
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )
            val etlProcessData = call.receiveOrNull<AbstractEtlProcess>()
                ?: throw ApiException("The provided ETL process definition cannot be parsed")
            if (etlProcessData.isActive == null) throw ApiException("An activation status for ETL process is required")
            dataStoreService.changeEtlProcessActivationState(
                pathParams.dataStoreId,
                pathParams.etlProcessId,
                etlProcessData.isActive
            )

            call.respond(HttpStatusCode.NoContent)
        }

        put<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Writer
            )

            val etlProcessData = call.receiveOrNull<AbstractEtlProcess>()
                ?: throw ApiException("The provided ETL process definition cannot be parsed")
            if (etlProcessData.dataConnectorId == null) throw ApiException("A data connector reference is required")
            if (etlProcessData.name.isNullOrBlank()) throw ApiException("A name for ETL process is required")

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
                        etlProcessData.configuration ?: throw ApiException("Empty ETL configuration is not supported")
                    dataStoreService.saveJdbcEtlProcess(
                        etlProcessData.id,
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        configuration
                    )
                }

                else -> throw ApiException("The provided ETL process type is not supported")
            }

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
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
                throw ApiException("Not found", HttpStatusCode.NotFound)
            }
        }

        delete<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            dataStoreService.removeEtlProcess(pathParams.dataStoreId, pathParams.etlProcessId)

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.SamplingEtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.organizationId,
                pathParams.dataStoreId,
                RoleType.Owner
            )
            val nComponents = (pathParams.nComponents ?: defaultSampleSize).coerceAtMost(maxSampleSize)
            val etlProcessData = call.receiveOrNull<AbstractEtlProcess>()
                ?: throw ApiException("The provided ETL process definition cannot be parsed")
            val id = when (etlProcessData.type) {
                EtlProcessType.jdbc -> {
                    etlProcessData.configuration ?: throw ApiException("Empty ETL configuration is not supported")
                    etlProcessData.dataConnectorId ?: throw ApiException("Unknown data connector ID is not supported")
                    etlProcessData.name ?: throw ApiException("Unknown ETL process name is not supported")
                    dataStoreService.createSamplingJdbcEtlProcess(
                        pathParams.dataStoreId,
                        etlProcessData.dataConnectorId,
                        etlProcessData.name,
                        etlProcessData.configuration,
                        nComponents
                    )
                }

                else -> throw ApiException("The provided ETL process type is not supported")
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
