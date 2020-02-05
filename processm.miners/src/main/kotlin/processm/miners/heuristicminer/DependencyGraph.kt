package processm.miners.heuristicminer

import kotlin.reflect.typeOf

internal class DependencyGraph(
    val log: SimplifiedLog,
    val sigma_l1l: Double = .9,
    val sigma_l2l: Double = .9,
    val sigma_a: Double = .9,
    val sigma_r: Double = .05
) {
    internal val certainty = computeCertainty()
    internal val certainty2 = computeCertainty2()
    internal val c1 = log.tasksSeq.filter { a -> certainty[a, a] >= sigma_l1l }
    internal val c2 = log.tasksPairsSeq.filterPairs { a, b ->
        !c1.contains(a) &&
                !c1.contains(b) &&
                certainty2[a, b] >= sigma_l2l
    }
    internal val strongestFollowers = log.tasksPairsSeq.filterPairs { a, b ->
        !log.isEnd(a) && !log.isEnd(b) && a != b && log.tasksSeq.all { y -> certainty[a, b] >= certainty[a, y] }
    }
    internal val cout = strongestFollowers.filterPairs { a, x ->
        !(certainty[a, x] < sigma_a &&
                strongestFollowers.anyPair { b, y ->
                    c2.contains(Pair(a, b)) && certainty[b, y] - certainty[a, x] > sigma_r
                })
    }
    internal val cbout =
        log.tasksPairsSeq.filterPairs { a, b -> certainty[a, b] >= sigma_a || cout.anyPair { x, c -> a == x && certainty[a, c] - certainty[a, b] < sigma_r } }
    internal val strongestCause = log.tasksPairsSeq.filterPairs { a, b ->
        !log.isStart(a) && !log.isStart(b) && a != b && log.tasksSeq.all { x -> certainty[a, b] >= certainty[x, b] }
    }
    internal val cin = strongestCause.filterPairs { x, a ->
        !(
                certainty[x, a] < sigma_a &&
                        strongestCause.anyPair { y, b ->
                            c2.contains(Pair(a, b)) && certainty[y, b] - certainty[x, a] > sigma_r
                        })
    }
    internal val cbin =
        log.tasksPairsSeq.filterPairs { b, a -> certainty[b, a] >= sigma_a || cout.anyPair { x, c -> b == x && certainty[b, c] - certainty[b, a] < sigma_r } }
    internal val shortEdges =
        c1.map { Edge(it, it, false) } + (c2 + cbout + cbin).map { Edge(it.first, it.second, false) }

    init {
        println(computeCertainty())
        println(computeCertainty2())
        println(log.tasksSeq.toList())
        println(log.tasksPairsSeq.toList())
        println("COUT ${cout.toList()}")
        println("CIN ${cin.toList()}")
        println("SHORT ${shortEdges.toList()}")
    }

    internal fun computeCertainty(): SquareMatrix<Double> {
        val directSuccessor = SquareMatrix(log.nTasks, 0)
        val certainty = SquareMatrix(log.nTasks, 0.0)
        log.forEach { trace ->
            (trace.subList(1, trace.size) zip trace.subList(
                0,
                trace.size - 1
            )).forEach { directSuccessor[it.first, it.second]++ }
        }
        for (a in 0 until log.nTasks) {
            for (b in 0 until log.nTasks) {
                if (a != b) {
                    certainty[a, b] =
                        (directSuccessor[a, b] - directSuccessor[b, a]) / (directSuccessor[a, b] + directSuccessor[b, a] + 1.0)
                } else {
                    certainty[a, b] = directSuccessor[a, a] / (directSuccessor[a, a] + 1.0)
                }
            }
        }
        return certainty
    }

    internal fun computeCertainty2(): SquareMatrix<Double> {
        val l2loop = SquareMatrix(log.nTasks, 0)
        for (trace in log) {
            for (i in 0 until trace.size - 2) {
                val a = trace[i]
                val b = trace[i + 1]
                if (a == trace[i + 2] && a != b) {
                    l2loop[a, b]++
                }
            }
        }
        val result = SquareMatrix(log.nTasks, 0.0)
        for (a in 0 until log.nTasks) {
            for (b in 0 until log.nTasks) {
                result[a, b] = (l2loop[a, b] + l2loop[b, a]) / (l2loop[a, b] + l2loop[b, a] + 1.0)
            }
        }
        return result
    }

}