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
import processm.services.api.models.DataSource
import processm.services.api.models.DataSourceCollectionMessageBody
import processm.services.api.models.DataSourceMessageBody
import processm.services.logic.DataSourceService
import processm.services.logic.LogsService
import java.io.OutputStream
import java.util.*
import java.util.zip.ZipException
import javax.xml.stream.XMLStreamException

@KtorExperimentalLocationsAPI
fun Route.DataSourcesApi() {
    val dataSourceService by inject<DataSourceService>()
    val logsService by inject<LogsService>()


    authenticate {
        post<Paths.DataSources> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            val messageBody = call.receiveOrNull<DataSourceMessageBody>()?.data
                ?: throw ApiException("The provided data source data cannot be parsed")

            principal.ensureUserBelongsToOrganization(pathParams.organizationId)

            if (messageBody.name.isEmpty()) throw ApiException("Data source name needs to be specified")
            val ds =
                dataSourceService.createDataSource(organizationId = pathParams.organizationId, name = messageBody.name)
            call.respond(HttpStatusCode.Created, DataSourceMessageBody(DataSource(name = ds.name, id = ds.id.value)))
        }

        get<Paths.DataSources> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)
            val dataSources = dataSourceService.allByOrganizationId(organizationId = pathParams.organizationId).map {
                DataSource(it.name, it.id, it.creationDate)
            }.toTypedArray()

            call.respond(HttpStatusCode.OK, DataSourceCollectionMessageBody(dataSources))
        }

        post<Paths.Logs> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!

            try {
                val part = call.receiveMultipart().readPart()

                if (part is PartData.FileItem) {
                    part.streamProvider().use { requestStream ->
                        logsService.saveLogFile(pathParams.dataSourceId, part.originalFileName, requestStream)
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
                    formatter = logsService::queryDataSourceJSON
                }
                "application/zip" -> {
                    mime = ContentType.Application.Zip
                    formatter = logsService::queryDataSourceZIPXES
                    call.response.header(
                        HttpHeaders.ContentDisposition,
                        ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, "xes.zip")
                            .toString()
                    )
                }
                else -> throw ApiException("Unsupported content-type.")
            }

            try {
                val queryProcessor = formatter(pathParams.dataSourceId, query)
                call.respondOutputStream(mime, HttpStatusCode.OK) {
                    queryProcessor(this)
                }
            } catch (e: RecognitionException) {
                throw ApiException(e.message)
            } catch (e: IllegalArgumentException) {
                throw ApiException(e.message)
            }
        }
    }
}
