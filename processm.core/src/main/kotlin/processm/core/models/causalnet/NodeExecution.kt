package processm.core.models.causalnet

class NodeExecution internal constructor(
    activity: Node,
    val instance: MutableModelInstance,
    join: Join?,
    split: Split?
) : DecoupledNodeExecution(activity, join, split) {
    override fun execute() = instance.execute(join, split)
}