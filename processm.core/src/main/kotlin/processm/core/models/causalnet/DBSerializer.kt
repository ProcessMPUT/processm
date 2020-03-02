package processm.core.models.causalnet

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool

/**
 * A replacement for [org.jetbrains.exposed.dao.id.IntIdTable], to avoid mixing DAO and DSL
 */
internal abstract class TIntId : Table() {
    val id = integer("id").autoIncrement()
    override val primaryKey: PrimaryKey = PrimaryKey(id)
}

internal object TModel : TIntId() {
    val start = (integer("start") references TNode.id).nullable()
    val end = (integer("end") references TNode.id).nullable()
}

internal object TNode : TIntId() {
    val activity = varchar("activity", 100)
    val instance = varchar("instance", 100)
    val special = bool("special")
    val model = integer("model") references TModel.id

    init {
        index(true, activity, instance, special, model)
    }
}

internal object TDependency : TIntId() {
    val depsource = reference("source", TNode.id)
    val deptarget = reference("target", TNode.id)
    val model = integer("model") references TModel.id

    init {
        index(true, depsource, deptarget)
    }
}

internal object TBinding : TIntId() {
    val isJoin = bool("isjoin")
    val model = integer("model") references TModel.id
}

internal object TDependencyBindings : TIntId() {
    val dependency = reference("dependency", TDependency.id)
    val binding = reference("binding", TBinding.id)
    val model = integer("model") references TModel.id

    init {
        index(true, dependency, binding, model)
    }
}

object DBSerializer {

    /**
     * Insert a causal net model to the DB
     *
     * Does not handle metadata nor decision models
     */
    fun insert(model: Model): Int {
        var result: Int? = null
        transaction(DBConnectionPool.database) {
            addLogger(StdOutSqlLogger)
            SchemaUtils.createMissingTablesAndColumns(TNode, TModel, TDependency, TDependencyBindings, TBinding)
            val modelId = TModel.insert { } get TModel.id
            result = modelId
            val nodeId = model.instances.map { node ->
                node to (TNode.insert {
                    it[activity] = node.activity
                    it[instance] = node.instanceId
                    it[special] = node.special
                    it[TNode.model] = modelId
                } get TNode.id)
            }.toMap()
            TModel.update({ TModel.id eq modelId }) {
                it[start] = nodeId[model.start]
                it[end] = nodeId[model.end]
            }
            val depId = model.outgoing.values.flatten().map { dep ->
                dep to (TDependency.insert {
                    it[depsource] = nodeId.getValue(dep.source)
                    it[deptarget] = nodeId.getValue(dep.target)
                    it[TDependency.model] = modelId
                } get TDependency.id)
            }.toMap()
            val tmp = model.joins.values.asSequence().flatten().map { join -> Pair(join, true) } +
                    model.splits.values.asSequence().flatten().map { join -> Pair(join, false) }
            tmp.forEach { (bdg, flag) ->
                val bindingId = TBinding.insert {
                    it[isJoin] = flag
                    it[TBinding.model] = modelId
                } get TBinding.id
                bdg.dependencies.forEach { dep: Dependency ->
                    TDependencyBindings.insert {
                        it[binding] = bindingId
                        it[dependency] = depId.getValue(dep)
                        it[TDependencyBindings.model] = modelId
                    }
                }
            }
        }
        if (result != null)
            return result!!
        else
            throw IllegalStateException()   //this should be impossible to reach
    }

    /**
     * Fetch a model with specified modelId from the DB
     *
     * Decision model and metadata handlers are left default
     */
    fun fetch(modelId: Int): MutableModel {
        var result: MutableModel? = null
        transaction(DBConnectionPool.database) {
            val idNode = TNode.select { TNode.model eq modelId }
                .map { row -> row[TNode.id] to Node(row[TNode.activity], row[TNode.instance], row[TNode.special]) }
                .toMap()
            val modelRow = TModel.select { TModel.id eq modelId }.single()
            val startId = modelRow[TModel.start]
            val endId = modelRow[TModel.end]
            if (startId == null || endId == null)
                throw IllegalStateException("start or end is null") //this means that DB went bonkers
            val mm = MutableModel(start = idNode.getValue(startId), end = idNode.getValue(endId))
            mm.addInstance(*idNode.values.toTypedArray())
            val idDep = TDependency.select { TDependency.model eq modelId }.map { row ->
                row[TDependency.id] to Dependency(
                    idNode.getValue(row[TDependency.depsource]),
                    idNode.getValue(row[TDependency.deptarget])
                )
            }.toMap()
            idDep.values.forEach { dep -> mm.addDependency(dep) }
            TBinding.select { TBinding.model eq modelId }.forEach { bindingRow ->
                val deps = TDependencyBindings.select { TDependencyBindings.binding eq bindingRow[TBinding.id] }
                    .map { db -> idDep.getValue(db[TDependencyBindings.dependency]) }
                    .toSet()
                if (bindingRow[TBinding.isJoin])
                    mm.addJoin(Join(deps))
                else
                    mm.addSplit(Split(deps))
            }
            result = mm
        }
        if (result != null)
            return result!!
        else
            throw IllegalStateException() // this should be impossible to reach
    }

    /**
     * Deletes a model with specified modelId
     */
    fun delete(modelId: Int) {
        transaction(DBConnectionPool.database) {
            TDependencyBindings.deleteWhere { TDependencyBindings.model eq modelId }
            TBinding.deleteWhere { TBinding.model eq modelId }
            TDependency.deleteWhere { TDependency.model eq modelId }
            TModel.update({ TModel.id eq modelId }) {
                it[start] = null
                it[end] = null
            }
            TNode.deleteWhere { TNode.model eq modelId }
            TModel.deleteWhere { TModel.id eq modelId }
        }
    }
}