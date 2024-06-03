package processm.dbmodels.models

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
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
const val WORKSPACE_COMPONENT_EVENT_DATA = "eventData"

enum class WorkspaceComponentEventType {
    /**
     * Event triggered when the system changed the component data.
     */
    DataChange,
    Delete,
    ComponentCreatedOrUpdated,

    /**
     * Triggered by an ETL process once new, possibly relevant data becomes available
     */
    NewExternalData,

    /**
     * Triggered when a new model is required, e.g., because a concept drift was observed
     */
    NewModelRequired,

    /**
     * Triggered once the user accepts a new model or the first model is mined for a component
     */
    ModelAccepted,
}


const val DATA_CHANGE_MODEL = "model"
const val DATA_CHANGE_ALIGNMENT_KPI = "alignmentKPI"
const val DATA_CHANGE_LAST_ERROR = "lastError"

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
     * Component-specific properties stored as a json map.
     */
    val properties = text("properties").nullable()

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
    var properties by WorkspaceComponents.properties.transform(
        { Json.encodeToString(it) },
        {
            it?.let { (Json.parseToJsonElement(it) as? JsonObject)?.mapValues { it.value.jsonPrimitive.content } }
                ?: emptyMap()
        }
    )
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

    @Deprecated("This is not a separate UI component", level = DeprecationLevel.ERROR)
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
