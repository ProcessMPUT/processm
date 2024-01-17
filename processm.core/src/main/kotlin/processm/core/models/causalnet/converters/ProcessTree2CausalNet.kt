package processm.core.models.causalnet.converters

import processm.core.helpers.mapToSet
import processm.core.models.causalnet.*
import processm.core.models.metadata.*
import processm.core.models.processtree.*
import processm.core.verifiers.CausalNetVerifier
import java.util.*
import processm.core.models.processtree.Node as TNode

/**
 * Class for rewriting process trees into causal nets. It rewrites the given [tree] recursively, one node at the time.
 * The [CausalNet] created this way has redundant parts that are removed in postprocessing.
 */
private class ProcessTree2CausalNet(private val tree: ProcessTree) {

    private var instanceCounter = 1
    private val activities = IdentityHashMap<ProcessTreeActivity, Node>().apply {
        val unique = HashMap<String, Int>()
        for (activity in tree.activities) {
            val name = if (activity.name.isEmpty()) activity.symbol else activity.name
            val id = unique.compute(name) { _, old -> old?.let { it + 1 } ?: 0 }
            val node = Node(
                activity = name,
                instanceId = if (id == 0) "" else id.toString(),
                isSilent = activity.isSilent
            )
            val old = put(activity, node)
            check(old === null) { "Duplicate activity identity $activity in process tree $tree" }
        }
    }
    private val metadataHandler = DefaultMutableMetadataHandler()
    private val dependencyMetadata = HashMap<MetadataSubject, SingleDoubleMetadata>()

    init {
        metadataHandler.addMetadataProvider(
            DefaultMetadataProvider(
                BasicMetadata.DEPENDENCY_MEASURE,
                dependencyMetadata
            )
        )
    }


    private fun convert(tnode: TNode): MutableCausalNet =
        when (tnode) {
            is Sequence -> convertSequence(tnode)
            is Exclusive -> convertExclusive(tnode)
            is Parallel -> convertParallel(tnode)
            is RedoLoop -> convertRedoLoop(tnode)
            is ProcessTreeActivity -> convertActivity(tnode)
            else -> throw IllegalArgumentException("Unknown node type: ${tnode.javaClass.name}")
        }

    private fun convertSequence(tnode: Sequence): MutableCausalNet {
        val subnets = tnode.children.map { convert(it) }

        require(subnets.size >= 1)

        val first = subnets.first()
        val last = subnets.last()
        val cnet = MutableCausalNet(first.start, last.end, metadataHandler)

        for (subnet in subnets) {
            rewrite(subnet, cnet)
        }

        for ((prev, next) in subnets.windowed(2, partialWindows = false)) {
            val dep = cnet.addDependency(prev.end, next.start)
            cnet.addSplit(Split(setOf(dep)))
            cnet.addJoin(Join(setOf(dep)))
            tnode.getDependencyMeasure()?.let { dependencyMetadata[dep] = it }
        }

        return cnet
    }

    private fun convertExclusive(tnode: Exclusive): MutableCausalNet {
        val subnets = tnode.children.map { convert(it) }

        require(subnets.size >= 1)

        val start = Node("→${tnode.symbol}", instanceCounter.toString(), true)
        val end = Node("${tnode.symbol}→", instanceCounter++.toString(), true)
        val cnet = MutableCausalNet(start, end, metadataHandler = metadataHandler)

        for (subnet in subnets) {
            rewrite(subnet, cnet)

            val startDep = cnet.addDependency(cnet.start, subnet.start)
            cnet.addSplit(Split(setOf(startDep)))
            cnet.addJoin(Join(setOf(startDep)))

            val endDep = cnet.addDependency(subnet.end, cnet.end)
            cnet.addSplit(Split(setOf(endDep)))
            cnet.addJoin(Join(setOf(endDep)))

            tnode.getDependencyMeasure()?.let {
                dependencyMetadata[startDep] = it
                dependencyMetadata[endDep] = it
            }
        }

        return cnet
    }

    private fun convertParallel(tnode: Parallel): MutableCausalNet {
        val subnets = tnode.children.map { convert(it) }

        require(subnets.size >= 1)

        val start = Node("→${tnode.symbol}", instanceCounter.toString(), true)
        val end = Node("${tnode.symbol}→", instanceCounter++.toString(), true)
        val cnet = MutableCausalNet(start, end, metadataHandler = metadataHandler)
        val startDeps = HashSet<Dependency>()
        val endDeps = HashSet<Dependency>()

        for (subnet in subnets) {
            rewrite(subnet, cnet)

            val startDep = cnet.addDependency(cnet.start, subnet.start)
            startDeps.add(startDep)
            cnet.addJoin(Join(setOf(startDep)))

            val endDep = cnet.addDependency(subnet.end, cnet.end)
            endDeps.add(endDep)
            cnet.addSplit(Split(setOf(endDep)))

            tnode.getDependencyMeasure()?.let {
                dependencyMetadata[startDep] = it
                dependencyMetadata[endDep] = it
            }
        }

        cnet.addSplit(Split(startDeps))
        cnet.addJoin(Join(endDeps))

        return cnet
    }

    private fun convertRedoLoop(tnode: RedoLoop): MutableCausalNet {
        val subnets = tnode.children.map { convert(it) }

        require(subnets.size >= 1)

        val start = Node("→${tnode.symbol}", instanceCounter.toString(), true)
        val end = Node("${tnode.symbol}→", instanceCounter++.toString(), true)
        val cnet = MutableCausalNet(start, end, metadataHandler = metadataHandler)

        for (subnet in subnets) {
            rewrite(subnet, cnet)
        }

        val first = subnets.first()
        val remaining = subnets.subList(1, subnets.size)

        val startDep = cnet.addDependency(cnet.start, first.start)
        cnet.addSplit(Split(setOf(startDep)))
        cnet.addJoin(Join(setOf(startDep)))

        val endDep = cnet.addDependency(first.end, cnet.end)
        cnet.addSplit(Split(setOf(endDep)))
        cnet.addJoin(Join(setOf(endDep)))

        tnode.getDependencyMeasure()?.let {
            dependencyMetadata[startDep] = it
            dependencyMetadata[endDep] = it
        }

        for (subnet in remaining) {
            val startDep = cnet.addDependency(first.end, subnet.start)
            cnet.addSplit(Split(setOf(startDep)))
            cnet.addJoin(Join(setOf(startDep)))

            val endDep = cnet.addDependency(subnet.end, first.start)
            cnet.addSplit(Split(setOf(endDep)))
            cnet.addJoin(Join(setOf(endDep)))

            tnode.getDependencyMeasure()?.let {
                dependencyMetadata[startDep] = it
                dependencyMetadata[endDep] = it
            }
        }

        return cnet
    }

    private fun convertActivity(tnode: ProcessTreeActivity): MutableCausalNet {
        val activity = activities[tnode]!!
        return MutableCausalNet(activity, activity, metadataHandler = metadataHandler)
    }

    private fun rewrite(
        subnet: CausalNet,
        cnet: MutableCausalNet
    ) {
        cnet.copyFrom(subnet) { node -> node }
        for (oldDep in subnet.dependencies) {
            assert(cnet.dependencies.any { it == oldDep }) { "Below code works only if dependencies from subnet equal depndencies added to cnet" }

            subnet.getAllMetadata(oldDep)[BasicMetadata.DEPENDENCY_MEASURE]?.let {
                dependencyMetadata[oldDep] = it as SingleDoubleMetadata
            }
        }

    }

    private fun simplify(cnet: MutableCausalNet) {
        // replace silent activities with a single input dependency by reattaching its splits to the previous node
        for (silent in cnet.instances.filter { it.isSilent && cnet.joins[it]?.size == 1 && cnet.joins[it]!!.first().size == 1 }) {
            // rewrite bindings & dependencies
            val silentJoin = cnet.joins[silent]!!.first()
            val silentSplits = cnet.splits[silent].orEmpty()
            val prevActivity = silentJoin.sources.first()
            val prevSplits = cnet.splits[prevActivity].orEmpty().filter { silent in it.targets }

            if (silent === cnet.end && cnet.outgoing[prevActivity]!!.size > 1)
                continue // the new end activity must not allow exiting

            for (silentSplit in silentSplits) {
                val newDeps = silentSplit.targets.mapToSet { cnet.addDependency(prevActivity, it) }
                for (dep in newDeps) {
                    val join = Join(setOf(dep))
                    if (join !in cnet.joins[dep.target].orEmpty())
                        cnet.addJoin(join)
                }
                for (prevSplit in prevSplits) {
                    val deps = newDeps + prevSplit.targets.mapNotNullTo(HashSet()) {
                        if (it == silent) null else cnet.addDependency(prevActivity, it)
                    }
                    cnet.addSplit(Split(deps))

                    for (dep in deps) {
                        silentSplit.dependencies.maxOf {
                            val v = cnet.getAllMetadata(it)[BasicMetadata.DEPENDENCY_MEASURE] as SingleDoubleMetadata?
                            v?.value ?: 0.0
                        }.let {
                            dependencyMetadata[dep] = SingleDoubleMetadata(it)
                        }
                    }
                }
            }

            if (silent === cnet.end)
                cnet.setEnd(prevActivity)

            // remove old structures
            cnet.outgoing[silent]?.forEach { dependencyMetadata.remove(it) }
            cnet.incoming[silent]?.forEach { dependencyMetadata.remove(it) }
            cnet.removeInstance(silent)
        }

        // replace silent activities with a single output dependency by reattaching its joins to the next node
        for (silent in cnet.instances.filter { it.isSilent && cnet.splits[it]?.size == 1 && cnet.splits[it]!!.first().size == 1 }) {
            // rewrite bindings & dependencies
            val silentSplit = cnet.splits[silent]!!.first()
            val silentJoins = cnet.joins[silent].orEmpty()
            val nextActivity = silentSplit.targets.first()
            val nextJoins = cnet.joins[nextActivity].orEmpty().filter { silent in it.sources }

            if (silent === cnet.start && cnet.incoming[nextActivity]!!.size > 1)
                continue // the new start activity must not be revisited

            for (silentJoin in silentJoins) {
                val newDeps = silentJoin.sources.mapToSet { cnet.addDependency(it, nextActivity) }
                for (dep in newDeps) {
                    val split = Split(setOf(dep))
                    if (split !in cnet.splits[dep.source].orEmpty())
                        cnet.addSplit(split)
                }
                for (nextJoin in nextJoins) {
                    val deps = newDeps + nextJoin.sources.mapNotNullTo(HashSet()) {
                        if (it == silent) null else cnet.addDependency(it, nextActivity)
                    }
                    cnet.addJoin(Join(deps))

                    for (dep in deps) {
                        silentSplit.dependencies.maxOf {
                            val v = cnet.getAllMetadata(it)[BasicMetadata.DEPENDENCY_MEASURE] as SingleDoubleMetadata?
                            v?.value ?: 0.0
                        }.let {
                            dependencyMetadata[dep] = SingleDoubleMetadata(it)
                        }
                    }
                }
            }

            if (silent === cnet.start)
                cnet.setStart(nextActivity)

            // remove old structures
            cnet.outgoing[silent]?.forEach { dependencyMetadata.remove(it) }
            cnet.incoming[silent]?.forEach { dependencyMetadata.remove(it) }
            cnet.removeInstance(silent)
        }

        // replace silent start activity
        do {
            val startSplits = cnet.splits[cnet.start]!!
            if (cnet.start.isSilent &&
                startSplits.size == 1 &&
                startSplits.first().size == 1 &&
                cnet.incoming[startSplits.first().targets.first()]!!.size == 1
            ) {
                val prev = cnet.setStart(startSplits.first().targets.first())
                cnet.outgoing[prev]?.forEach { dependencyMetadata.remove(it) }
                cnet.removeInstance(prev)
            } else break
        } while (true)

        // replace silent end activity
        do {
            val endJoins = cnet.joins[cnet.end]!!
            if (cnet.end.isSilent &&
                endJoins.size == 1 &&
                endJoins.first().size == 1 &&
                cnet.outgoing[endJoins.first().sources.first()]!!.size == 1
            ) {
                val prev = cnet.setEnd(endJoins.first().sources.first())
                cnet.incoming[prev]?.forEach { dependencyMetadata.remove(it) }
                cnet.removeInstance(prev)
            } else break
        } while (true)
    }

    private fun TNode.getDependencyMeasure(): SingleDoubleMetadata? =
        tree.getAllMetadata(this)[BasicMetadata.DEPENDENCY_MEASURE] as SingleDoubleMetadata?

    fun toCausalNet(): CausalNet {
        requireNotNull(tree.root) { "Tree root must not be null." }


        val cnet = convert(tree.root!!)

        // verify assertion only in test code even if -ea parameter is passed to JVM
        assert(!Thread.currentThread().stackTrace.any { it.className.startsWith("org.junit") } || {
            val verifier = CausalNetVerifier()
            val report = verifier.verify(cnet)
            report.isSound
        }())

        simplify(cnet)

        return cnet
    }
}

/**
 * Converts this [ProcessTree] into [CausalNet].
 */
fun ProcessTree.toCausalNet(): CausalNet = ProcessTree2CausalNet(this).toCausalNet()
