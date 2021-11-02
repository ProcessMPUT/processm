package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object WorkspaceComponents : UUIDTable("workspace_components") {
    /**
     * The machine-readable name of this component.
     */
    val name = text("name")

    /**
     * The identifier of the workspace containing this component.
     */
    val workspaceId = reference("workspace_id", Workspaces)

    /**
     * The PQL query associated with this component.
     */
    val query = text("query")

    /**
     * The id of the data store holding the underlying log data.
     */
    val dataStoreId = uuid("data_store_id")

    /**
     * The type of this component. See [ComponentTypeDto].
     */
    val componentType = text("type")

    /**
     * The data of this component. Every component may store its own data here, e.g., process model,
     * database identifiers, a state.
     */
    val data = text("data").nullable()

    /**
     * The data associated with this component by the services module and/or GUI.
     */
    val customizationData = text("customization_data").nullable()

    /**
     * The position and size of the component.
     */
    val layoutData = text("layout_data").nullable()
}

class WorkspaceComponent(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WorkspaceComponent>(
        WorkspaceComponents
    )

    var name by WorkspaceComponents.name
    var workspace by Workspace referencedOn WorkspaceComponents.workspaceId
    var query by WorkspaceComponents.query
    var dataStoreId by WorkspaceComponents.dataStoreId
    var componentType by WorkspaceComponents.componentType.transform(
        { it.typeName },
        { ComponentTypeDto.byTypeNameInDatabase(it) })
    var data by WorkspaceComponents.data
    var customizationData by WorkspaceComponents.customizationData
    var layoutData by WorkspaceComponents.layoutData

    fun toDto() = WorkspaceComponentDto(
        id.value,
        name,
        query,
        dataStoreId,
        componentType,
        data,
        customizationData = customizationData,
        layoutData = layoutData
    )
}

enum class ComponentTypeDto(val typeName: String) {
    CausalNet("causalNet"),
    BPMN("bpmn"),
    Kpi("kpi");

    companion object {
        fun byTypeNameInDatabase(typeNameInDatabase: String) = values().first { it.typeName == typeNameInDatabase }
    }
}

data class WorkspaceComponentDto(
    val id: UUID,
    val name: String,
    val query: String = "SELECT ...",
    val dataStore: UUID,
    val componentType: ComponentTypeDto,
    val data: Any? = null,
    val customizationData: String? = null,
    val layoutData: String? = null
)

