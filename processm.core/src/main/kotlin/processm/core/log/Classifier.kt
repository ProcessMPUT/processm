package processm.core.log

/**
 * A classifier assigns an identity to each event that makes it comparable to others (via their assigned identity).
 *
 * Can be declared on <trace> or <event> level.
 * Default: <event>
 */
class Classifier(name: String?, keys: String?) {
    /**
     * The name of the classifier
     */
    val name: String? = name?.intern()
    /**
     * The white-space-separated list of attribute keys that constitute this classifier.
     * These attributes shall be declared global at the proper (either event or trace) level.
     */
    val keys: String? = keys?.intern()
}