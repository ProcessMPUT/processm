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
import processm.core.helpers.mapToSet
import processm.core.persistence.DBConnectionPool
import java.util.*
import kotlin.NoSuchElementException

internal class DAOModel(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAOModel>(CausalNetModel)

    var start by CausalNetModel.start
    var end by CausalNetModel.end
    val nodes by DAONode referrersOn CausalNetNode.model
    val dependencies by DAODependency referrersOn CausalNetDependency.model
    val bindings by DAOBinding referrersOn CausalNetBinding.model
}

internal object CausalNetModel : IntIdTable() {
    val start = reference("start", CausalNetNode, onDelete = ReferenceOption.CASCADE).nullable()
    val end = reference("end", CausalNetNode, onDelete = ReferenceOption.CASCADE).nullable()
}

internal class DAONode(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAONode>(CausalNetNode)

    var activity by CausalNetNode.activity
    var instance by CausalNetNode.instance
    var special by CausalNetNode.special
    var model by DAOModel referencedOn CausalNetNode.model
}

internal object CausalNetNode : IntIdTable() {
    val activity = varchar("activity", 100)
    val instance = varchar("instance", 100)
    val special = bool("special")
    val model = reference("model", CausalNetModel, onDelete = ReferenceOption.CASCADE)

    init {
        index(true, activity, instance, special, model)
    }
}

internal class DAODependency(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<DAODependency>(CausalNetDependency)

    var source by CausalNetDependency.depsource
    var target by CausalNetDependency.deptarget
    var model by DAOModel referencedOn CausalNetDependency.model
}

internal object CausalNetDependency : IntIdTable() {
    val depsource = reference("source", CausalNetNode, onDelete = ReferenceOption.CASCADE)
    val deptarget = reference("target", CausalNetNode, onDelete = ReferenceOption.CASCADE)
    val model = reference("model", CausalNetModel, onDelete = ReferenceOption.CASCADE)

    init {
        index(true, depsource, deptarget)
    }
}

/**
 * [UUIDEntity] instead of [IntEntity] to provide randomized UUIDs by hand, to avoid splitting the inserting transaction into two
 */
internal class DAOBinding(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<DAOBinding>(CausalNetBinding)

    var isJoin by CausalNetBinding.isJoin
    var model by DAOModel referencedOn CausalNetBinding.model
    var dependencies by DAODependency via CausalNetDependencyBindings
}

internal object CausalNetBinding : UUIDTable() {
    val isJoin = bool("isjoin")
    val model = reference("model", CausalNetModel, onDelete = ReferenceOption.CASCADE)
}

internal object CausalNetDependencyBindings : Table() {
    val dependency = reference("dependency", CausalNetDependency, onDelete = ReferenceOption.CASCADE)
    val binding = reference("binding", CausalNetBinding, onDelete = ReferenceOption.CASCADE)

    override val primaryKey = PrimaryKey(dependency, binding)
}

object DBSerializer {

    /**
     * Insert a causal net model to the DB
     *
     * Does not handle metadata nor decision models
     */
    fun insert(model: CausalNet): Int {
        var result: Int? = null
        transaction(DBConnectionPool.database) {
            addLogger(Slf4jSqlDebugLogger)
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
    fun fetch(modelId: Int): MutableCausalNet {
        var result: MutableCausalNet? = null
        transaction(DBConnectionPool.database) {
            addLogger(Slf4jSqlDebugLogger)
            val daomodel = DAOModel.findById(modelId) ?: throw NoSuchElementException()
            val idNode = daomodel.nodes
                .map { row -> row.id to Node(row.activity, row.instance, row.special) }
                .toMap()
            var start = daomodel.start
            var end = daomodel.end
            if (start == null || end == null)
                throw IllegalStateException("start or end is null") //this means that DB went bonkers
            val mm = MutableCausalNet(start = idNode.getValue(start), end = idNode.getValue(end))
            mm.addInstance(*idNode.values.toTypedArray())
            val idDep = daomodel.dependencies.map { row ->
                row to mm.addDependency(
                    idNode.getValue(row.source),
                    idNode.getValue(row.target)
                )
            }.toMap()
            daomodel.bindings.forEach { row ->
                val deps = row.dependencies.mapToSet { idDep.getValue(it) }
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
            addLogger(Slf4jSqlDebugLogger)
            val model = DAOModel.findById(modelId) ?: throw NoSuchElementException()
            model.delete()
        }
    }
}