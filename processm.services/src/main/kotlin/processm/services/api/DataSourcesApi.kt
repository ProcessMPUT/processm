package processm.services.api

import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.koin.ktor.ext.inject
import processm.services.api.models.DataSource
import processm.services.api.models.DataSourceCollectionMessageBody
import processm.services.api.models.DataSourceMessageBody
import processm.services.logic.DataSourceService
import java.time.Instant
import java.time.ZoneOffset

@KtorExperimentalLocationsAPI
fun Route.DataSourcesApi() {
    val service by inject<DataSourceService>()

    authenticate {
        post<Paths.DataSources> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            val messageBody = call.receiveOrNull<DataSourceMessageBody>()?.data
                ?: throw ApiException("The provided data source data cannot be parsed")

            principal.ensureUserBelongsToOrganization(pathParams.organizationId)

            if (messageBody.name.isEmpty()) throw ApiException("Data source name needs to be specified")

            val ds = service.createDataSource(organizationId = pathParams.organizationId, name = messageBody.name)
            call.respond(HttpStatusCode.Created, DataSourceMessageBody(DataSource(name = ds.name, id = ds.id.value)))
        }

        get<Paths.DataSources> { pathParams ->
            val principal = call.authentication.principal<ApiUser>()!!
            principal.ensureUserBelongsToOrganization(pathParams.organizationId)

            val dataSources = service.allByOrganizationId(organizationId = pathParams.organizationId)
                .map {
                    val instant = Instant.ofEpochMilli(it.creationDate.millis)
                    DataSource(it.name, it.id, java.time.LocalDateTime.ofInstant(instant, ZoneOffset.UTC))
                }
                .toTypedArray()

            call.respond(HttpStatusCode.OK, DataSourceCollectionMessageBody(dataSources))
        }
    }
}
