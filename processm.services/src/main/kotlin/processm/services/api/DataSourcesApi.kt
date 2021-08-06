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
import processm.services.api.models.DataStore
import processm.services.api.models.DataStoreCollectionMessageBody
import processm.services.api.models.DataStoreMessageBody
import processm.services.logic.DataStoreService
import processm.services.logic.LogsService
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipException
import javax.xml.stream.XMLStreamException

@KtorExperimentalLocationsAPI
fun Route.DataStoresApi() {
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
                DataStore(it.name, it.id, it.creationDate)
            }.toTypedArray()

            call.respond(HttpStatusCode.OK, DataStoreCollectionMessageBody(dataStores))
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
            }
        }
    }
}
