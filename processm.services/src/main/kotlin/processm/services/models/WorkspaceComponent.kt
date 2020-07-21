package processm.services.models

import java.util.*

data class WorkspaceComponentDto(val id: UUID, val name: String, val query: String = "SELECT ...", val data: Any)

data class CausalNetNodeDto(val id: String, val splits: Array<Array<String>>, val joins: Array<Array<String>>)
data class CausalNetEdgeDto(val sourceNodeId: String, val targetNodeId: String)
data class CausalNetDto(val nodes: List<CausalNetNodeDto>, val edges: List<CausalNetEdgeDto>)