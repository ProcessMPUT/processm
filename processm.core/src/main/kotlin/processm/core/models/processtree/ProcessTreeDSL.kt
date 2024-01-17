package processm.core.models.processtree

fun processTree(node: () -> Node?): ProcessTree {
    return ProcessTree(root = node.invoke())
}

