package processm.core.models.commons

interface AbstractActivityExecution {
    val activity: AbstractActivity
    fun execute()
}