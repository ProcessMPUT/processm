package processm.services.api

import io.ktor.auth.Principal

data class ApiUser(val userId: String) : Principal {

}
