package processm.core.models.causalnet

import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.DBConnectionPool
import java.util.*
import kotlin.NoSuchElementException

internal class DAOModel(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAOModel>(TModel)

    var start by TModel.start
    var end by TModel.end
    val nodes by DAONode referrersOn TNode.model
    val dependencies by DAODependency referrersOn TDependency.model
    val bindings by DAOBinding referrersOn TBinding.model
}

internal object TModel : IntIdTable() {
    val start = reference("start", TNode, onDelete = ReferenceOption.CASCADE).nullable()
    val end = reference("end", TNode, onDelete = ReferenceOption.CASCADE).nullable()
}

internal class DAONode(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAONode>(TNode)

    var activity by TNode.activity
    var instance by TNode.instance
    var special by TNode.special
    var model by DAOModel referencedOn TNode.model
}

internal object TNode : IntIdTable() {
    val activity = varchar("activity", 100)
    val instance = varchar("instance", 100)
    val special = bool("special")
    val model = reference("model", TModel, onDelete = ReferenceOption.CASCADE)

    init {
        index(true, activity, instance, special, model)
    }
}

internal class DAODependency(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAODependency>(TDependency)

    var source by TDependency.depsource
    var target by TDependency.deptarget
    var model by DAOModel referencedOn TDependency.model
}

internal object TDependency : IntIdTable() {
    val depsource = reference("source", TNode, onDelete = ReferenceOption.CASCADE)
    val deptarget = reference("target", TNode, onDelete = ReferenceOption.CASCADE)
    val model = reference("model", TModel, onDelete = ReferenceOption.CASCADE)

    init {
        index(true, depsource, deptarget)
    }
}

/**
 * [UUIDEntity] instead of [IntEntity] to provide randomized UUIDs by hand, to avoid splitting the inserting transaction into two
 */
internal class DAOBinding(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DAOBinding>(TBinding)

    var isJoin by TBinding.isJoin
    var model by DAOModel referencedOn TBinding.model
    var dependencies by DAODependency via TDependencyBindings
}

internal object TBinding : UUIDTable() {
    val isJoin = bool("isjoin")
    val model = reference("model", TModel, onDelete = ReferenceOption.CASCADE)
}

internal object TDependencyBindings : Table() {
    val dependency = reference("dependency", TDependency, onDelete = ReferenceOption.CASCADE)
    val binding = reference("binding", TBinding, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(dependency, binding)
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
            val daomodel = DAOModel.new {
            }
            val node2DAONode = model.instances.map { node ->
                node to DAONode.new {
                    activity = node.activity
                    instance = node.instanceId
                    special = node.special
                    this.model = daomodel
                }
            }.toMap()
            daomodel.start = node2DAONode.getValue(model.start).id
            daomodel.end = node2DAONode.getValue(model.end).id
            val dep2DAODep = model.outgoing.values.flatten().map { dep ->
                dep to DAODependency.new {
                    source = node2DAONode.getValue(dep.source).id
                    target = node2DAONode.getValue(dep.target).id
                    this.model = daomodel
                }
            }.toMap()
            val tmp = model.joins.values.asSequence().flatten().map { join -> Pair(join, true) } +
                    model.splits.values.asSequence().flatten().map { join -> Pair(join, false) }
            tmp.forEach { (bdg, flag) ->
                DAOBinding.new {
                    isJoin = flag
                    this.model = daomodel
                    dependencies = SizedCollection(bdg.dependencies.map { dep -> dep2DAODep.getValue(dep) })
                }
            }
            result = daomodel.id.value
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
            val daomodel = DAOModel.findById(modelId) ?: throw NoSuchElementException()
            val idNode = daomodel.nodes
                .map { row -> row.id to Node(row.activity, row.instance, row.special) }
                .toMap()
            var start = daomodel.start
            var end = daomodel.end
            if (start == null || end == null)
                throw IllegalStateException("start or end is null") //this means that DB went bonkers
            val mm = MutableModel(start = idNode.getValue(start), end = idNode.getValue(end))
            mm.addInstance(*idNode.values.toTypedArray())
            val idDep = daomodel.dependencies.map { row ->
                row to mm.addDependency(
                    idNode.getValue(row.source),
                    idNode.getValue(row.target)
                )
            }.toMap()
            daomodel.bindings.forEach { row ->
                val deps = row.dependencies.map { idDep.getValue(it) }.toSet()
                if (row.isJoin)
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
            DAOModel.findById(modelId)?.delete()
        }
    }
}