package processm.core.log

/**
 * Applies a limit on the total number of logs read from this stream.
 * The stream breaks on [n]+1 [Log] component (the [n]+1 [Log] is consumed from the base stream but not returned).
 */
fun XESInputStream.takeLogs(n: Int): XESInputStream {
    require(n >= 0) { "n must be non-negative" }
    var nLogs = 0
    return takeWhile {
        if (it is Log)
            ++nLogs
        nLogs <= n
    }
}

/**
 * Applies a limit on the total number of traces read from this stream.
 * The stream breaks on n+1 [Trace] component (the n+1 [Trace] is consumed from the base stream but not returned).
 */
fun XESInputStream.takeTraces(n: Int): XESInputStream {
    require(n >= 0) { "n must be non-negative" }
    var nTraces = 0
    return takeWhile {
        if (it is Trace)
            ++nTraces
        nTraces <= n
    }
}
