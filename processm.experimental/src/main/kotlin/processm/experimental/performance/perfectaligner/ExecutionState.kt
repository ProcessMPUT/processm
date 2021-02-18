package processm.experimental.performance.perfectaligner

import processm.experimental.helpers.BitIntSet


class ExecutionState(val model: IntCausalNet) {
    val tokens = IntArray(model.nDeps)
    var nTokens = 0
    val activeJoins = BitIntSet(model.nJoins)
    val activeDeps = BitIntSet(model.nDeps)
    val activeNodes = BitIntSet(model.nNodes)   //nodes with at least one active join

    fun copy(): ExecutionState {
        val result = ExecutionState(model)
        tokens.copyInto(result.tokens)
        result.nTokens = nTokens
        result.activeJoins.addAll(this.activeJoins)
        result.activeDeps.addAll(this.activeDeps)
        result.activeNodes.addAll(this.activeNodes)
        return result
    }

    fun executeJoin(joinIdx: Int) {
        val deadJoins = ArrayList<Int>()
        val join = model.flatJoins[joinIdx]
        for (dep in join) {
            assert(tokens[dep] >= 1)
            tokens[dep] -= 1
            if (tokens[dep] == 0) {
                activeDeps.remove(dep)
                for (possiblyDeadJoin in activeJoins)
                    if (dep in model.flatJoins[possiblyDeadJoin])
                        deadJoins.add(possiblyDeadJoin)
            }
        }
        assert(joinIdx in deadJoins)
        activeJoins.removeAll(deadJoins)
        activeNodes.clear()
        for (join in activeJoins)
            activeNodes.add(model.join2Target[join])
        nTokens -= join.size
    }

    fun executeSplit(splitIdx: Int) {
        val split = model.flatSplits[splitIdx]
        for (dep in split) {
            tokens[dep] += 1
            if (tokens[dep] == 1)
                activeDeps.add(dep)
        }
        for (joinIdx in model.split2Joins[splitIdx]) {
            val join = model.flatJoins[joinIdx]
            if (activeDeps.containsAll(join)) {
                activeJoins.add(joinIdx)
                activeNodes.add(model.join2Target[joinIdx])
            }
        }
        nTokens += split.size
    }
}
