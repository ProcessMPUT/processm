package processm.core.models.causalnet

/**
 * [DecoupledNodeExecution] with concrete [ModelInstance] to provide context for execution
 */
class NodeExecution internal constructor(
    activity: Node,
    val instance: MutableModelInstance,
    join: Join?,
    split: Split?
) : DecoupledNodeExecution(activity, join, split) {
    override fun execute() = instance.execute(join, split)
}