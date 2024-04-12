package processm.conformance

import processm.conformance.models.DeviationType
import processm.conformance.models.alignments.Alignment
import processm.conformance.models.alignments.Step
import processm.core.log.Event
import processm.core.log.Helpers
import processm.core.models.causalnet.Node
import processm.core.models.commons.Activity


/**
 * A crude DSL to create mock [Alignment]s
 *
 * ```
alignment {
"a" with ("person" to "A") executing "a"
null executing "c"
"b" with ("person" to "B") executing "b"
"d" executing "d"
"c" executing null
}
```
This generates an alignment
```
a| |b|d|c
---------
a|c|b|d|

where the event "a" has an additional attribute "person" with the value "A", and the event "b" an additional attribute
"person" with the value "B"
```
 */
fun alignment(init: AlignmentDSL.() -> Unit): Alignment {
    val dsl = AlignmentDSL()
    dsl.init()
    return dsl.result()
}

class AlignmentDSL {

    private val steps = ArrayList<Step>()

    var cost: Int = 0

    class EventAux(var name: String) {

        private val attrs = ArrayList<Pair<String, Any>>()

        fun toEvent(): Event = Helpers.event(name, *attrs.toTypedArray())

        infix fun with(v: Pair<String, Any>): EventAux {
            attrs.add(v)
            return this
        }
    }

    class StepAux(val event: Event?, val activity: Activity?)

    /**
     * @param activityAndCause The first item is activity, the second item is collection of activites that directly caused the first item.
     */
    infix fun Event?.executing(activityAndCause: Pair<Activity?, Collection<Activity>>): Unit {
        steps.add(
            Step(
                modelMove = activityAndCause.first,
                modelState = null,
                modelCause = activityAndCause.second,
                logMove = this,
                logState = null,
                type = if (activityAndCause.first !== null) if (this !== null) DeviationType.None else DeviationType.ModelDeviation else DeviationType.LogDeviation
            )
        )
    }

    infix fun Event?.executing(a: Activity?) = this executing (a to emptyList<Activity>())

    @JvmName("executingString")
    infix fun Event?.executing(activityAndCause: Pair<String?, Collection<String>>) =
        this executing (activityAndCause.first?.let { Node(it) } to activityAndCause.second.map { Node(it) })

    infix fun Event?.executing(a: String?) = this executing if (a !== null) Node(a) else null

    infix fun String.executing(a: Activity?) = EventAux(this) executing a

    infix fun String.executing(a: String) = EventAux(a) executing Node(a)

    infix fun String.executing(activityAndCause: Pair<String?, Collection<String>>) =
        EventAux(this) executing activityAndCause

    infix fun EventAux.executing(a: String) = this.toEvent() executing Node(a)

    infix fun EventAux.executing(a: Activity?) = this.toEvent() executing a

    infix fun EventAux.executing(activityAndCause: Pair<String?, Collection<String>>) =
        this.toEvent() executing activityAndCause

    infix fun String.with(v: Pair<String, Any>) = EventAux(this) with v

    fun String.asEvent() = EventAux(this)


    fun result(): Alignment = Alignment(steps, cost)

}
