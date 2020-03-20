package processm.core.models.processtree

fun processTree(node: () -> Node?): Model {
    return Model(root = node.invoke())
}

