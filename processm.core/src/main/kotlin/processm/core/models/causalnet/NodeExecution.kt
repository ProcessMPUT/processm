package processm.core.models.causalnet

/**
 * [DecoupledNodeExecution] with concrete [CausalNetInstance] to provide context for execution
 */
class NodeExecution internal constructor(
        activity: Node,
        val instance: MutableCausalNetInstance,
        join: Join?,
        split: Split?
) : DecoupledNodeExecution(activity, join, split) {
    override fun execute() = instance.execute(join, split)
}