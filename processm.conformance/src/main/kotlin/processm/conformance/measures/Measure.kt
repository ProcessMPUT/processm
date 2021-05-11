package processm.conformance.measures

fun interface Measure<T, out R> {
    operator fun invoke(artifact: T): R
}