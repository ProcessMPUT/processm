package processm.core.log

class Classifier(name: String?, keys: String?) {
    val name: String? = name?.intern()
    val keys: String? = keys?.intern()
}