package processm.core.log

interface XESOutputStream : AutoCloseable {
    fun write(element: XESElement)
}