package processm.core.models.commons

interface AbstractModel {
    val activities: Sequence<AbstractActivity>
    val startActivities: Sequence<AbstractActivity>
    val endActivities: Sequence<AbstractActivity>
    val decisionPoints: Sequence<AbstractDecisionPoint>
    fun createInstance(): AbstractModelInstance
}