package processm.conformance.models.alignments

data class PenaltyFunction(
    val synchronousMove: Int = 0,
    val silentMove: Int = 0,
    val modelMove: Int = 1,
    val logMove: Int = 1
)
