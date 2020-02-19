package processm.core.log

class DatabaseXESInputStream(private val query: Any) : XESInputStream {
    override fun iterator(): Iterator<XESElement> = sequence<XESElement> {
        TODO(
            "Find and fetch XES elements from database. This function is called for the first element being fetched " +
                    "from this sequence and then suspended on the first yield() statement. By fetching a next element from " +
                    "this sequence, this function is resumed and processing advances toward the next yield statement."
        )
//        yield(Log()) // Return Log element
//        yield(Trace()) // Return Trace
//        yield(Event()) // Return Event
    }.iterator()
}
