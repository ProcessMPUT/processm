package processm.services.api


import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.locations.*
import io.ktor.locations.post
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import org.antlr.v4.runtime.RecognitionException
import org.koin.ktor.ext.inject
import processm.core.helpers.mapToArray
import processm.dbmodels.models.OrganizationRoleDto
import processm.services.api.models.*
import processm.services.logic.DataStoreService
import processm.services.logic.LogsService
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipException
import javax.xml.stream.XMLStreamException

@KtorExperimentalLocationsAPI
fun Route.DataStoresApi() {
    val connectionStringPropertyName = "connection-string"
    val dataStoreService by inject<DataStoreService>()
    val logsService by inject<LogsService>()

    authenticate {
        post<Paths.DataStores> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            val messageBody = call.receiveOrNull<DataStoreMessageBody>()?.data
                ?: throw ApiException("The provided data store data cannot be parsed")

            principal.ensureUserBelongsToOrganization(pathParams.organizationId)

            if (messageBody.name.isEmpty()) throw ApiException("Data store name needs to be specified")
            val ds =
                dataStoreService.createDataStore(organizationId = pathParams.organizationId, name = messageBody.name)
            call.respond(HttpStatusCode.Created, DataStoreMessageBody(DataStore(name = ds.name, id = ds.id.value)))
        }

        get<Paths.DataStores> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            val dataStores = dataStoreService.allByOrganizationId(organizationId = pathParams.organizationId).map {
                DataStore(it.name, it.id, null, it.creationDate)
            }.toTypedArray()

            call.respond(HttpStatusCode.OK, DataStoreCollectionMessageBody(dataStores))
        }

        get<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            val dataStoreSize = dataStoreService.getDatabaseSize(pathParams.dataStoreId.toString())
            val dataStore = dataStoreService.getDataStore(pathParams.dataStoreId)

            call.respond(
                HttpStatusCode.OK, DataStoreMessageBody(
                    DataStore(
                        dataStore.name,
                        dataStore.id,
                        dataStoreSize.toInt(),
                        dataStore.creationDate
                    )
                )
            )
        }

        delete<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                OrganizationRoleDto.Owner
            )
            dataStoreService.removeDataStore(pathParams.dataStoreId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataStore> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                OrganizationRoleDto.Owner
            )
            val dataStore = call.receiveOrNull<DataStoreMessageBody>()?.data
                ?: throw ApiException("The provided data store data cannot be parsed")
            dataStoreService.renameDataStore(pathParams.dataStoreId, dataStore.name)

            call.respond(HttpStatusCode.NoContent)
        }

        post<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!

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
            val query = call.parameters["query"] ?: ""
            val accept = call.request.accept() ?: "application/json";
            val mime: ContentType
            val formatter: (uuid: UUID, query: String) -> OutputStream.() -> Unit
            when (accept) {
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
                else -> throw ApiException("Unsupported content-type.")
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
                pathParams.dataStoreId,
                OrganizationRoleDto.Owner
            )
            logsService.removeLog(pathParams.dataStoreId, pathParams.identityId)

            call.respond(HttpStatusCode.NoContent)
        }

        get<Paths.DataConnectors> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            val dataConnectors = dataStoreService.getDataConnectors(pathParams.dataStoreId).mapToArray {
                DataConnector(
                    it.id,
                    it.name,
                    it.lastConnectionStatus,
                    it.lastConnectionStatusTimestamp?.toString(),
                    it.connectionProperties
                )
            }

            call.respond(HttpStatusCode.OK, DataConnectorCollectionMessageBody(dataConnectors))
        }

        post<Paths.DataConnectors> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            val dataConnector = call.receiveOrNull<DataConnectorMessageBody>()?.data
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
                DataConnectorMessageBody(
                    DataConnector(
                        dataConnectorId,
                        connectorName,
                        lastConnectionStatus = null,
                        lastConnectionStatusTimestamp = null
                    )
                )
            )
        }

        delete<Paths.DataConnector> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                OrganizationRoleDto.Owner
            )
            dataStoreService.removeDataConnector(pathParams.dataStoreId, pathParams.dataConnectorId)

            call.respond(HttpStatusCode.NoContent)
        }

        patch<Paths.DataConnector> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(
                principal.userId,
                pathParams.dataStoreId,
                OrganizationRoleDto.Owner
            )
            val dataConnector = call.receiveOrNull<DataConnectorMessageBody>()?.data
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
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            val connectionProperties = call.receiveOrNull<DataConnectorMessageBody>()?.data?.properties
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
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            val caseNotionSuggestions = dataStoreService.getCaseNotionSuggestions(pathParams.dataStoreId, pathParams.dataConnectorId)
                .mapToArray { (classes, relations) ->
                    CaseNotion(classes.toMap(), relations.mapToArray { (sourceClass, targetClass) -> CaseNotionEdges("$sourceClass", "$targetClass") })
                }

            call.respond(HttpStatusCode.OK,
                CaseNotionCollectionMessageBody(caseNotionSuggestions))
        }

        get<Paths.RelationshipGraph> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            val (classes, relations) = dataStoreService.getRelationshipGraph(pathParams.dataStoreId, pathParams.dataConnectorId)
            val caseNotionWithAllClasses = CaseNotion(classes.toMap(), relations.mapToArray { (sourceClass, targetClass) -> CaseNotionEdges("$sourceClass", "$targetClass") })

            call.respond(HttpStatusCode.OK,
                CaseNotionMessageBody(caseNotionWithAllClasses))
        }

        get<Paths.EtlProcesses> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)

            val etlProcesses = dataStoreService.getEtlProcesses(pathParams.dataStoreId)
                .mapToArray { AbstractEtlProcess(it.name, it.dataConnectorId, EtlProcessType.valueOf(it.processType.processTypeName), it.id) }

            call.respond(HttpStatusCode.OK,
                EtlProcessCollectionMessageBody(etlProcesses))
        }

        post<Paths.EtlProcesses> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(principal.userId, pathParams.dataStoreId, OrganizationRoleDto.Owner)
            val etlProcessData = call.receiveOrNull<EtlProcessMessageBody>()?.data
                ?: throw ApiException("The provided ETL process definition cannot be parsed")
            when (etlProcessData.type) {
                EtlProcessType.automatic -> {
                    val relations = etlProcessData.caseNotion?.edges.orEmpty().map { edge ->
                        edge.sourceClassId to edge.targetClassId
                    }
                    dataStoreService.createAutomaticEtlProcess(pathParams.dataStoreId, etlProcessData.dataConnectorId, etlProcessData.name, relations)
                }
                else -> throw ApiException("The provided ETL process type is not supported")
            }

            call.respond(HttpStatusCode.Created,
                EtlProcessMessageBody(AbstractEtlProcess(etlProcessData.name, etlProcessData.dataConnectorId, etlProcessData.type, etlProcessData.id)))
        }

        delete<Paths.EtlProcess> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            dataStoreService.assertUserHasSufficientPermissionToDataStore(principal.userId, pathParams.dataStoreId, OrganizationRoleDto.Owner)
            dataStoreService.assertDataStoreBelongsToOrganization(pathParams.organizationId, pathParams.dataStoreId)
            dataStoreService.removeEtlProcess(pathParams.dataStoreId, pathParams.etlProcessId)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}
