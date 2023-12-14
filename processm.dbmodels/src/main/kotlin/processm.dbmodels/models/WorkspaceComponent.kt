package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant
import java.util.*

/**
 * The JMS topic in which the changes to the components are announced.
 */
const val WORKSPACE_COMPONENTS_TOPIC = "workspace_components"

/**
 * The body key of the JMS message containing the id of the component as string.
 */
const val WORKSPACE_COMPONENT_ID = "id"

const val WORKSPACE_ID = "workspace_id"

/**
 * The property of the JMS message containing the type of the component as string.
 */
const val WORKSPACE_COMPONENT_TYPE = "componentType"

const val WORKSPACE_COMPONENT_EVENT = "event"

const val CREATE_OR_UPDATE = "create_or_update"

const val DELETE = "delete"

const val DATA_CHANGE = "data_change"

object WorkspaceComponents : UUIDTable("workspace_components") {
    /**
     * The machine-readable name of this component (the configuration parameter).
     */
    val name = text("name")

    /**
     * The identifier of the workspace containing this component (the configuration parameter).
     */
    val workspaceId = reference("workspace_id", Workspaces)

    /**
     * The PQL query associated with this component (the configuration parameter).
     */
    val query = text("query")

    /**
     * The algorithm used to calculate data. The interpretation of this property is component-specific.
     */
    val algorithm = text("algorithm").nullable()

    /**
     * The type of the model associated with this component (the configuration parameter).
     */
    val modelType = text("model_type").nullable()

    /**
     * The id of the model associated with this component (the configuration parameter).
     */
    val modelId = long("model_id").nullable()

    /**
     * The id of the data store holding the underlying log data (the configuration parameter).
     */
    val dataStoreId = uuid("data_store_id")

    /**
     * The type of this component (the configuration parameter). See [ComponentTypeDto].
     */
    val componentType = text("type")

    /**
     * The data of this component. Every component may store its own data here, e.g., process model,
     * database identifiers, a state (the computed value).
     */
    val data = text("data").nullable()

    /**
     * The data associated with this component by the services module and/or GUI (the computed value).
     */
    val customizationData = text("customization_data").nullable()

    /**
     * The position and size of the component (the computed value).
     */
    val layoutData = text("layout_data").nullable()

    /**
     * The timestamp of the last modification made by a user (the computed value).
     */
    val userLastModified = timestamp("user_last_modified").clientDefault(Instant::now)

    /**
     * The timestamp of the last modification made by the system (the computed value).
     */
    val dataLastModified = timestamp("data_last_modified").nullable()

    /**
     * The description of the last error that occurred for this component (the computed value).
     */
    val lastError = text("last_error").nullable()

    /**
     * True marks components marked for deletion.
     */
    val deleted = bool("deleted").default(false)
}

class WorkspaceComponent(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WorkspaceComponent>(
        WorkspaceComponents
    )

    var name by WorkspaceComponents.name
    var workspace by Workspace referencedOn WorkspaceComponents.workspaceId
    var query by WorkspaceComponents.query
    var algorithm by WorkspaceComponents.algorithm
    var modelType by WorkspaceComponents.modelType.transform(
        { it?.typeName },
        { ModelTypeDto.byTypeNameInDatabase(it) }
    )
    var modelId by WorkspaceComponents.modelId
    var dataStoreId by WorkspaceComponents.dataStoreId
    var componentType by WorkspaceComponents.componentType.transform(
        { it.typeName },
        { ComponentTypeDto.byTypeNameInDatabase(it) })
    var data by WorkspaceComponents.data
    var customizationData by WorkspaceComponents.customizationData
    var layoutData by WorkspaceComponents.layoutData
    var userLastModified by WorkspaceComponents.userLastModified
    var dataLastModified by WorkspaceComponents.dataLastModified
    var lastError by WorkspaceComponents.lastError
    var deleted by WorkspaceComponents.deleted
}

enum class ComponentTypeDto(val typeName: String) {
    CausalNet("causalNet"),
    BPMN("bpmn"),
    Kpi("kpi"),
    AlignerKpi("alignerKpi"),
    PetriNet("petriNet"),
    TreeLogView("treeLogView"),
    FlatLogView("flatLogView"),
    DirectlyFollowsGraph("directlyFollowsGraph");

    companion object {
        fun byTypeNameInDatabase(typeNameInDatabase: String) = try {
            values().first { it.typeName == typeNameInDatabase }
        } catch (e: NoSuchElementException) {
            throw IllegalArgumentException("Unknown component type: $typeNameInDatabase", e)
        }
    }

    override fun toString(): String = typeName
}

enum class ModelTypeDto(val typeName: String) {
    CausalNet("causalNet"),
    ProcessTree("processTree"),
    PetriNet("petriNet");

    companion object {
        fun byTypeNameInDatabase(typeNameInDatabase: String?) =
            ModelTypeDto.values().firstOrNull { it.typeName == typeNameInDatabase }
    }
}



