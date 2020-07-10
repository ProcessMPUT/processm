package processm.experimental.helpers

class InlineIf<out T> internal constructor(internal val ifTrue: T)

infix fun <T> Boolean.then(ifTrue: T): InlineIf<T>? = if (this) InlineIf(
    ifTrue
) else null

infix fun <T> InlineIf<T>?.els(otherwise: T): T = if (this !== null) this.ifTrue else otherwise

inline infix fun <T : Any> Boolean.then(ifTrue: T): T? = if (this) ifTrue else null
inline infix fun <T> T?.els(otherwise: T): T = this ?: otherwise

inline infix fun <T> Boolean.then(noinline ifTrue: () -> T): (() -> T)? = if (this) ifTrue else null
inline infix fun <T> (() -> T)?.els(noinline otherwise: () -> T): T = (this ?: otherwise)()

fun test() {
    val a: Long? = null
    val b: Int = 3
    val c: Double = 4.0

    // nullable then
    val z1 = (a == b.toLong()) then a els c - 2
    val z2: Number? = (a == b.toLong()) then a els c - 2
    val z3 = (a == b.toLong()) then a els null

    // not-null then
    val z4 = (a == b.toLong()) then c els a
    val z5 = (a == b.toLong()) then c els null
    val z6: Number = (a == b.toLong()) then b els c - 2
    val z7: Int? = (a == b.toLong()) then b

    // lazy-form then
    val z8 = (a == b.toLong()) then { c } els { a }
    val z9 = (a == b.toLong()) then { c } els { null }
    val za: Number = (a == b.toLong()) then { b } els { c - 2 }
}

