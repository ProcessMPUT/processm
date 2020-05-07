package processm.core.log

/**
 * The interface of XES Input reader
 *
 * The implementing class is required to return a [Sequence] of [XESElement]s. It is, however, free to return a flat
 * sequence (e.g., [Log], [Trace], [Event], [Event], [Trace], [Event],...) or a hierarchical sequence of just
 * [processm.core.log.hierarchical.Log]s.
 * The implementing class is required to map custom XES attributes in the resulting collection [XESElement.attributes]
 * to their standard names and fields, e.g., [XESElement.conceptName], [XESElement.identityId]. The other way mapping is
 * not required.
 */
typealias XESInputStream = Sequence<XESElement>