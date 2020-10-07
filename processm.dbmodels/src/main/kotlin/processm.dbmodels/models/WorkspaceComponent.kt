package processm.dbmodels.models

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import java.util.*

object WorkspaceComponents : UUIDTable("workspace_components") {
    val name = text("name")
    val workspaceId = reference("workspace_id", Workspaces)
    val query = text("query")
    val componentType = text("type")
    val componentDataSourceId = integer("data_source_id")
    val customizationData = text("customization_data").nullable()
}

class WorkspaceComponent(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<WorkspaceComponent>(
        WorkspaceComponents
    )

    var name by WorkspaceComponents.name
    var workspace by Workspace referencedOn WorkspaceComponents.workspaceId
    var query by WorkspaceComponents.query
    var componentType by WorkspaceComponents.componentType.transform(
        { it.typeName }, {
            ComponentTypeDto.byTypeNameInDatabase(
                it
            )
        })
    var componentDataSourceId by WorkspaceComponents.componentDataSourceId
    var customizationData by WorkspaceComponents.customizationData

    fun toDto() = WorkspaceComponentDto(
        id.value,
        name,
        query,
        componentType,
        customizationData = customizationData
    )
}

enum class ComponentTypeDto(val typeName: String) {
    CausalNet("causalNet"),
    Kpi("kpi");

    companion object {
        fun byTypeNameInDatabase(typeNameInDatabase: String) = values().first {it.typeName == typeNameInDatabase }
    }
}

data class WorkspaceComponentDto(val id: UUID, val name: String, val query: String = "SELECT ...", val componentType: ComponentTypeDto, var data: Any? = null, val customizationData: String? = null)
data class CausalNetNodeDto(val id: String, val splits: Array<Array<String>>, val joins: Array<Array<String>>)
data class CausalNetEdgeDto(val sourceNodeId: String, val targetNodeId: String)
data class CausalNetDto(val nodes: List<CausalNetNodeDto>, val edges: List<CausalNetEdgeDto>)