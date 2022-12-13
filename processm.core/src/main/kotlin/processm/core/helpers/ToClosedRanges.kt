package processm.core.helpers

fun List<Long>.toClosedRanges() = sequence<LongRange> {
    if (isEmpty())
        return@sequence
    if (size == 1) {
        yield(first()..first())
        return@sequence
    }
    with(sorted()) {
        assert(size > 1)
        var start = first()
        var end = start
        for (i in 1 until size) {
            if (end + 1 == this[i]) {
                end++
            } else {
                yield(start..end)
                start = this[i]
                end = start
            }
        }
        yield(start..end)
    }
}