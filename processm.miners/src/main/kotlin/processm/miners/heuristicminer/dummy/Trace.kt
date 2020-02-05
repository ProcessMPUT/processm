package processm.miners.heuristicminer.dummy

class Trace(events: List<Event>) {
    companion object {
        val start = Event("_start")
        val end = Event("_end")
    }

    val events = listOf(start) + events + listOf(end)

    override fun toString(): String {
        return "Trace($events)"
    }
}