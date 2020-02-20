package processm.core.log

/**
 * The interface of XES Input reader
 *
 * Class implementing this interface should return Sequence of XESElement (Log, Trace, Event, Event ...)
 */
interface XESInputStream : Sequence<XESElement>