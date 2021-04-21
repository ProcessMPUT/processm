package processm.conformance.models.alignments

import processm.conformance.models.DeviationType
import processm.core.helpers.indexOfFirst
import processm.core.helpers.zipOrThrow
import processm.core.log.Event

/**
 * Merges this list of [Alignment]s using the given list of [events]. The order of steps in all alignments must be
 * consistent with the order of [events].
 * The alignments must not contain duplicate steps. Use [mergeDuplicateAware] for alignments with duplicate steps.
 */
fun List<Alignment>.merge(events: List<Event>): Alignment {
    val currentSteps = IntArray(size)

    // merge subalignments
    val steps = ArrayList<Step>()
    for (event in events) {
        val prevSteps = steps.size
        for ((aIndex, subAlignment) in this.withIndex()) {
            val matchingEventIndex = subAlignment.steps.indexOfFirst(currentSteps[aIndex]) { it.logMove === event }
            if (matchingEventIndex >= 0) {
                steps.addAll(subAlignment.steps.subList(currentSteps[aIndex], matchingEventIndex + 1))
                currentSteps[aIndex] = matchingEventIndex + 1
                break
            }
        }
        assert(steps.size > prevSteps)
    }

    // merge model-only moves in an arbitrary order
    for (i in currentSteps.indices) {
        while (currentSteps[i] < this[i].steps.size) {
            val step = this[i].steps[currentSteps[i]]
            assert(step.type == DeviationType.ModelDeviation)
            steps.add(step)
            ++currentSteps[i]
        }
    }

    assert(currentSteps.withIndex().all { (i, s) -> s == this[i].steps.size })
    steps.verify(events)
    return Alignment(steps, this.sumOf(Alignment::cost))
}

/**
 * Merges this list of [Alignment]s using the given list of [events]. The order of steps in all alignments ust be
 * consistent with the order of [events].
 */
fun List<Alignment>.mergeDuplicateAware(events: List<Event>, penalty: PenaltyFunction): Alignment {
    val currentSteps = IntArray(size)

    // merge subalignments
    val steps = ArrayList<Step>()
    val modelMoves = ArrayList<Step>() // insertion order is important
    for (event in events) {
        var eventStep: Step? = null
        val skipIndices = HashSet<Int>()
        for ((aIndex, subAlignment) in this.withIndex()) {
            val matchingEventIndex = subAlignment.steps.indexOfFirst(currentSteps[aIndex]) { it.logMove === event }
            if (matchingEventIndex >= 0) {
                val matchingEventStep = subAlignment.steps[matchingEventIndex]
                assert(eventStep == null || eventStep == matchingEventStep)
                eventStep = matchingEventStep

                if (currentSteps[aIndex] < matchingEventIndex) {
                    val stepsBefore = subAlignment.steps.subList(currentSteps[aIndex], matchingEventIndex)
                    assert(stepsBefore.all { it.logMove === null })
                    val toAdd = stepsBefore.filter { it.modelMove?.isSilent == true || it !in modelMoves }
                    modelMoves.addAll(toAdd)
                }

                currentSteps[aIndex] = matchingEventIndex + 1
                skipIndices.add(aIndex)
            }
        }
        assert(eventStep !== null)

        // find the same model moves in other alignments and update indices
        if (modelMoves.isNotEmpty()) {
            for ((aIndex, subAlignment) in this.withIndex()) {
                if (aIndex in skipIndices || currentSteps[aIndex] >= subAlignment.steps.size)
                    continue

                val match = subAlignment.steps.matchingSteps(currentSteps[aIndex], modelMoves)
                assert(match >= currentSteps[aIndex])
                currentSteps[aIndex] = match
            }

            steps.addAll(modelMoves)
            modelMoves.clear()
        }

        steps.add(eventStep!!)
    }

    // merge model-only moves in an arbitrary order
    assert(modelMoves.isEmpty())
    for (i in currentSteps.indices) {
        val missingSteps = this[i].steps.subList(currentSteps[i], this[i].steps.size)
        assert(missingSteps.all { it.logMove === null })
        modelMoves.addAll(missingSteps)
    }
    steps.addAll(modelMoves)

    steps.verify(events)
    return Alignment(steps, steps.sumOf(penalty::calculate))
}

private fun List<Step>.matchingSteps(start: Int, other: Iterable<Step>): Int {
    val it1 = this.listIterator(start)
    val it2 = other.iterator()
    while (it1.hasNext() && it2.hasNext()) {
        var one: Step = it1.next()
        while (one.modelMove?.isSilent == true && it1.hasNext())
            one = it1.next()

        var two = it2.next()
        while (two.modelMove?.isSilent == true && it2.hasNext())
            two = it2.next()

        if (one != two)
            break
    }
    return maxOf(it1.previousIndex(), start)
}

/**
 * Fills this alignment with missing events from the given list of [events]. The order of steps in this alignment
 * must be consistent with the order of [events] excluding the missing events.
 */
fun Alignment.fillMissingEvents(events: List<Event>, penalty: PenaltyFunction): Alignment {
    val steps = ArrayList<Step>(this.steps)
    var cost = this.cost
    var stepIndex = 0
    for (event in events) {
        val index = steps.indexOfFirst(stepIndex) { it.logMove === event }
        if (index == -1) {
            // missing event, inserting
            steps.add(
                stepIndex, Step(
                    modelMove = null,
                    modelState = null, // FIXME: obtain model state
                    logMove = event,
                    logState = null, // FIXME: obtain log state
                    type = DeviationType.LogDeviation
                )
            )
            ++stepIndex
            cost += penalty.logMove
        } else {
            stepIndex = index + 1
        }
    }
    steps.verify(events)
    return Alignment(steps, cost)
}

/**
 * Verifies whether this list of alignment [Step]s is consistent with the given list of [events].
 */
fun List<Step>.verify(events: List<Event>) {
    assert((events.asSequence() zipOrThrow this.asSequence().mapNotNull(Step::logMove)).all { (e1, e2) -> e1 === e2 }) {
        "events:\t${events.joinToString { it.conceptName.toString() }}\n" +
                "alignment:\t${this.mapNotNull(Step::logMove).joinToString { it.conceptName.toString() }}"
    }

    assert(this.all { s -> s.type != DeviationType.None || s.logMove?.conceptName == s.modelMove?.name }) {
        Alignment(this.toList(), -1).toString()
    }
}
