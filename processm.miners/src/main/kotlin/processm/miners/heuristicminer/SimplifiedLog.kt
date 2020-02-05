package processm.miners.heuristicminer

import processm.miners.heuristicminer.dummy.Log
import processm.miners.heuristicminer.dummy.Trace

class SimplifiedLog(baselog: Log) {
    val idsToTasks =
        baselog.traces.asSequence().map { trace -> trace.events.map { event -> event.name } }.flatten().distinct()
            .toList()
    val tasksToIds =
        idsToTasks.mapIndexed { idx, task ->
            task to idx
        }.toMap()
    val log =
        baselog.traces.map { trace -> trace.events.map { event -> tasksToIds.getValue(event.name) } }
    val nTasks = idsToTasks.size

    val endId = tasksToIds[Trace.end.name]
    val startId = tasksToIds[Trace.start.name]

    val tasksSeq = (0 until nTasks).asSequence()
    val tasksPairsSeq = tasksSeq.flatMap { a -> tasksSeq.map { b -> a to b } }

    fun isStart(id: Int): Boolean = startId == id
    fun isEnd(id: Int): Boolean = endId == id

    init {
        println(idsToTasks)
        println(tasksToIds)
        println(log)
    }

    inline fun forEach(action: (List<Int>) -> Unit) {
        log.forEach(action)
    }

    operator fun iterator(): Iterator<List<Int>> = log.iterator()
}