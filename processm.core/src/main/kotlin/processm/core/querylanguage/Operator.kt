package processm.core.querylanguage

class Operator(val value: String) : Expression() {
    override fun toString(): String = value
}