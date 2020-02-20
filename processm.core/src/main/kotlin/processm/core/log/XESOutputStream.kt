package processm.core.log

/**
 * The interface XES Output
 *
 * Class with this interface should override function 'write' and store passed XESElement in memory / database etc.
 * With function `abort` user should be able to rollback transaction or remove objects from memory.
 */
interface XESOutputStream : AutoCloseable {
    fun write(element: XESElement)
    fun abort()
}