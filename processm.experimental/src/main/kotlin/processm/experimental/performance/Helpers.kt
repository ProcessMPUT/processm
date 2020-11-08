package processm.experimental.performance

fun<T> setCovering(interesting:Set<T>, sets:List<Set<T>>): List<Int> {
    fun helper(pos:Int, partialSolution:Set<T>, choosen:List<Int>, ub:Int):List<Int>? {
        if(choosen.size>=ub)
            return null
        if(pos >= sets.size)
            return null
        val newPartialSolution = partialSolution + sets[pos]
        val newChoosen =choosen + listOf(pos)
        if(newPartialSolution.containsAll(interesting))
            return newChoosen
        val result=helper(pos+1, newPartialSolution, newChoosen, ub)
        val result2= helper(pos+1, partialSolution, choosen, result?.size?:ub )
        return result2?:result
    }
    //require(universe.size<128)
    return helper(0, emptySet(), emptyList(), sets.size)?:throw IllegalStateException("This is unexpected")
}

/**
 *
 */
fun<T> notReallySetCovering(interesting:Set<T>, sets:List<Set<T>>): List<Int> {
    fun helper(pos: Int, partialSolution: Set<T>, choosen: List<Int>, ubTotalSize: Int, ubNSets: Int): Pair<List<Int>, Set<T>>? {
        if (partialSolution.size >= ubTotalSize || choosen.size >= ubNSets)
            return null
        if (pos >= sets.size)
            return null
        val newPartialSolution = partialSolution + sets[pos]
        val newChoosen = choosen + listOf(pos)
        val result = if (newPartialSolution.size <= ubTotalSize && newChoosen.size <= ubNSets && (newPartialSolution.size < ubTotalSize || newChoosen.size < ubNSets)) {
            if (newPartialSolution.containsAll(interesting))
                newChoosen to newPartialSolution
            else
                helper(pos + 1, newPartialSolution, newChoosen, ubTotalSize, ubNSets)
        } else null
        val ub2TotalSize = result?.second?.size
        val ub2NSets = result?.first?.size
        val result2 = helper(pos + 1, partialSolution, choosen, ub2TotalSize ?: ubTotalSize, ub2NSets?:ubNSets)
        return result2 ?: result
    }
    //require(universe.size<128)
    return helper(0, emptySet(), emptyList(), Integer.MAX_VALUE, Integer.MAX_VALUE)?.first?:throw IllegalStateException("This is unexpected")
}

fun<T, C:Collection<T>> separateDisjointFamilies(inp:Collection<C>):Map<Set<T>, List<C>> {
    val result=ArrayList<Pair<HashSet<T>, MutableList<C>>>()
    for(set in inp) {
        val relevantIndices = result.mapIndexedNotNull { idx, pair ->
            if(set.any { it in pair.first })
                idx
            else
                null
        }
        if(relevantIndices.isEmpty()) {
            result.add(HashSet(set) to mutableListOf(set))
        } else {
            val target =result[relevantIndices[0]]
            target.first.addAll(set)
            target.second.add(set)
            if(relevantIndices.size>1) {
                val rest = relevantIndices.subList(1, relevantIndices.size)
                for(idx in rest.sortedDescending()) {
                    target.first.addAll(result[idx].first)
                    target.second.addAll(result[idx].second)
                    result.removeAt(idx)
                }
            }
        }
    }
    return result.toMap()
}