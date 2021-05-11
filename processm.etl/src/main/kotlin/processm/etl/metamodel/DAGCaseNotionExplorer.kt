package processm.etl.metamodel

import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.transactions.transaction
import org.jgrapht.Graph
import org.jgrapht.GraphTests
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.shortestpath.DijkstraShortestPath
import org.jgrapht.alg.spanning.KruskalMinimumSpanningTree
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.graph.DefaultDirectedGraph
import processm.core.persistence.connection.DBCache
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.sqrt

/**
 * Explores possible case notions and evaluates their potential relevance.
 *
 * @param targetDatabaseName Name of database containing meta model data.
 * @param metaModelReader Component for acquiring meta model data.
 */
class DAGCaseNotionExplorer(private val targetDatabaseName: String, private val metaModelReader: MetaModelReader) {

    val vertexNames = mapOf(
        31 to "actor",
        32 to "category",
        33 to "country",
        34 to "language",
        35 to "city",
        36 to "film",
        37 to "address",
        38 to "film_actor",
        39 to "film_category",
        40 to "inventory",
        41 to "customer",
        42 to "staff",
        43 to "rental",
        44 to "store",
        45 to "payment",
        46 to "categories",
        47 to "customer_demographics",
        48 to "customers",
        49 to "employees",
        50 to "region",
        51 to "shippers",
        52 to "suppliers",
        53 to "us_states",
        54 to "customer_customer_demo",
        55 to "orders",
        56 to "products",
        57 to "territories",
        58 to "employee_territories",
        59 to "order_details",
        60 to "AWBuildVersion",
        61 to "DatabaseLog",
        62 to "ErrorLog",
        63 to "Department",
        64 to "Shift",
        65 to "Employee",
        66 to "EmployeeDepartmentHistory",
        67 to "EmployeePayHistory",
        68 to "JobCandidate",
        69 to "AddressType",
        70 to "BusinessEntity",
        71 to "ContactType",
        72 to "CountryRegion",
        73 to "PhoneNumberType",
        74 to "Person",
        75 to "BusinessEntityContact",
        76 to "EmailAddress",
        77 to "Password",
        78 to "PersonPhone",
        79 to "StateProvince",
        80 to "Address",
        81 to "BusinessEntityAddress",
        82 to "Culture",
        83 to "Illustration",
        84 to "Location",
        85 to "ProductCategory",
        86 to "ProductDescription",
        87 to "ProductModel",
        88 to "ProductPhoto",
        89 to "ScrapReason",
        90 to "TransactionHistoryArchive",
        91 to "UnitMeasure",
        92 to "ProductModelIllustration",
        93 to "ProductModelProductDescriptionCulture",
        94 to "ProductSubcategory",
        95 to "Product",
        96 to "BillOfMaterials",
        97 to "Document",
        98 to "ProductCostHistory",
        99 to "ProductInventory",
        100 to "ProductListPriceHistory",
        101 to "ProductProductPhoto",
        102 to "ProductReview",
        103 to "TransactionHistory",
        104 to "WorkOrder",
        105 to "ProductDocument",
        106 to "WorkOrderRouting",
        107 to "ShipMethod",
        108 to "Vendor",
        109 to "ProductVendor",
        110 to "PurchaseOrderHeader",
        111 to "PurchaseOrderDetail",
        112 to "CreditCard",
        113 to "Currency",
        114 to "SalesReason",
        115 to "SpecialOffer",
        116 to "CountryRegionCurrency",
        117 to "CurrencyRate",
        118 to "SalesTerritory",
        119 to "PersonCreditCard",
        120 to "SalesPerson",
        121 to "SalesTaxRate",
        122 to "ShoppingCartItem",
        123 to "SpecialOfferProduct",
        124 to "SalesPersonQuotaHistory",
        125 to "SalesTerritoryHistory",
        126 to "Store",
        127 to "Customer",
        128 to "SalesOrderHeader",
        129 to "SalesOrderDetail",
        130 to "SalesOrderHeaderSalesReason",
        131 to "trace_xe_action_map",
        132 to "trace_xe_event_map",
        133 to "actor",
        134 to "address",
        135 to "category",
        136 to "city",
        137 to "country",
        138 to "customer",
        139 to "film",
        140 to "film_actor",
        141 to "film_category",
        142 to "inventory",
        143 to "language",
        144 to "payment",
        145 to "payment_p2007_01",
        146 to "payment_p2007_02",
        147 to "payment_p2007_03",
        148 to "payment_p2007_04",
        149 to "payment_p2007_05",
        150 to "payment_p2007_06",
        151 to "rental",
        152 to "staff",
        153 to "store",
        154 to "Cities_Archive",
        155 to "Countries_Archive",
        156 to "DeliveryMethods_Archive",
        157 to "PaymentMethods_Archive",
        158 to "People",
        159 to "People_Archive",
        160 to "StateProvinces_Archive",
        161 to "TransactionTypes_Archive",
        162 to "Countries",
        163 to "DeliveryMethods",
        164 to "PaymentMethods",
        165 to "TransactionTypes",
        166 to "StateProvinces",
        167 to "Cities",
        168 to "SystemParameters",
        169 to "SupplierCategories_Archive",
        170 to "Suppliers_Archive",
        171 to "SupplierCategories",
        172 to "Suppliers",
        173 to "PurchaseOrders",
        174 to "PurchaseOrderLines",
        175 to "SupplierTransactions",
        176 to "BuyingGroups_Archive",
        177 to "CustomerCategories_Archive",
        178 to "Customers_Archive",
        179 to "BuyingGroups",
        180 to "CustomerCategories",
        181 to "Customers",
        182 to "Orders",
        183 to "Invoices",
        184 to "OrderLines",
        185 to "SpecialDeals",
        186 to "CustomerTransactions",
        187 to "InvoiceLines",
        188 to "trace_xe_action_map",
        189 to "trace_xe_event_map",
        190 to "ColdRoomTemperatures",
        191 to "ColdRoomTemperatures_Archive",
        192 to "Colors_Archive",
        193 to "PackageTypes_Archive",
        194 to "StockGroups_Archive",
        195 to "StockItems_Archive",
        196 to "VehicleTemperatures",
        197 to "Colors",
        198 to "PackageTypes",
        199 to "StockGroups",
        200 to "StockItems",
        201 to "StockItemHoldings",
        202 to "StockItemStockGroups",
        203 to "StockItemTransactions"
    )
    /**
     * Discovers case notions consisting of the specified classes.
     *
     * @param rootClassId Case notion's root class.
     * @param allowedClassesIds Set of classes which are allowed to be present in the resulting case notions.
     * @return A list containing case notions.
     * @see TreeCaseNotionDefinition.rootClass
     */
    fun discoverCaseNotions(performFullSearch: Boolean = false, goodEnoughScore: Double = 0.0): List<Pair<DAGBusinessPerspectiveDefinition<EntityID<Int>>, Double>>
            = transaction(DBCache.get(targetDatabaseName).database) {
        val relationshipGraph: Graph<EntityID<Int>, String> = DefaultDirectedGraph(String::class.java)
        val successors = mutableMapOf<EntityID<Int>, MutableSet<EntityID<Int>>>()
        val predecessors = mutableMapOf<EntityID<Int>, MutableSet<EntityID<Int>>>()

        metaModelReader.getRelationships()
            .forEach { (relationshipName, relationship) ->
                val (referencingClassId, referencedClassId) = relationship

                relationshipGraph.addVertex(referencingClassId)
                relationshipGraph.addVertex(referencedClassId)

                // self-loop, not supported at the moment
                if (referencingClassId != referencedClassId) {
                    relationshipGraph.addEdge(referencingClassId, referencedClassId, relationshipName)
                    successors.getOrPut(referencingClassId, { mutableSetOf() }).add(referencedClassId)
                    predecessors.getOrPut(referencedClassId, { mutableSetOf() }).add(referencingClassId)
                }
            }

        val weights = relationshipGraph.calculateVertexWeights()
//        val hubVertices = successors.filter { it.value.size > 1 }.keys
//        val rootConnectedSets = ConnectivityInspector(AsSubgraph(relationshipGraph, relationshipGraph.vertexSet() - hubVertices)).connectedSets()

//        return@transaction rootConnectedSets
//            .map { caseNotionsNodes ->
//                caseNotionsNodes
//                    .filter { predecessors.containsKey(it) }
//                    .map { predecessors[it]!!.filter { predecessor -> hubVertices.contains(predecessor) } }
//                    .flatten()
//                    .toMutableSet()
//                    .plus(caseNotionsNodes)
//            }
//            .map { extendedNodesSet -> AsSubgraph(relationshipGraph, extendedNodesSet) }
        return@transaction setOf(relationshipGraph)
            .map {
                val a = it.searchForOptimumBottomUp(weights, performFullSearch, goodEnoughScore)
                val b = a.map { it.first.vertexSet().map { vertex -> vertexNames[vertex.value]!! } to it.second }
                val bySize = b.groupBy { it.first.size }
                    .mapValues { it.value.size }

                a.map { DAGBusinessPerspectiveDefinition(it.first) to it.second }
            }
            .flatten()
    }

    private fun Graph<EntityID<Int>, String>.splitByEdge(splittingEdge: String): Set<Graph<EntityID<Int>, String>> {
        val splitGraph = AsSubgraph(this, vertexSet(), edgeSet() - splittingEdge)
        val connectedSets = ConnectivityInspector(splitGraph).connectedSets()

        return connectedSets.map { AsSubgraph(splitGraph, it) }.toSet()
    }

    private fun Graph<EntityID<Int>, String>.splitByVertex(splittingVertex: EntityID<Int>): Set<Graph<EntityID<Int>, String>> {
        val splitGraph = AsSubgraph(this, vertexSet() - splittingVertex)
        val connectedSets = ConnectivityInspector(splitGraph).connectedSets()

        return connectedSets.map { AsSubgraph(splitGraph, it) }.toSet()
    }

    private fun Graph<EntityID<Int>, String>.searchForOptimumTopDown(vertexWeights: Map<EntityID<Int>, Double>, performFullSearch: Boolean, goodEnoughScore: Double): List<Pair<Graph<EntityID<Int>, String>, Double>> {
        val bestSolutions = mutableSetOf<Pair<Graph<EntityID<Int>, String>, Double>>()
        val subgraphQueue = ArrayDeque(setOf((this to 0) to calculateEdgeHeterogeneity()))
        val edgeOrder = getEdgeOrdering2()

        while (subgraphQueue.isNotEmpty()) {
            val (graphInfo, parentScore) = subgraphQueue.removeLast()
            val (parentGraph, parentGraphInducingEdge) = graphInfo
            val childGraphs = if (parentScore <= goodEnoughScore) emptyList() else
                parentGraph.edgeSet()
                .filter { edgeOrder[it]!! >= parentGraphInducingEdge }
                .map { edgeName ->
                    val subgraphs = parentGraph.splitByEdge(edgeName)
                    val edgeOrdering = edgeOrder[edgeName]!!

                    return@map subgraphs.filter { it.vertexSet().size > 1 }
                        .map { subgraph -> (subgraph to edgeOrdering) to subgraph.calculateEdgeHeterogeneity() }
                        .filter { performFullSearch || it.second < parentScore }
                }
                .flatten()

            val bestChild = childGraphs.minByOrNull { it.first.second }

            //            if (bestChild != null) subgraphQueue.addAll(childGraphs)
            if (bestChild != null) subgraphQueue.add(bestChild)
            if (bestChild == null || performFullSearch) bestSolutions.add(parentGraph to parentScore)
        }

        return bestSolutions.sortedWith(
            compareBy(
                { it.second },
                { -it.first.vertexSet().sumOf { vertexWeights[it]!! } / it.first.vertexSet().size },
//                    { it.first.vertexSet().size / it.first.edgeSet().size }
                { (it.first.vertexSet().size - 1) * it.first.vertexSet().size / it.first.edgeSet().size }
        ))
    }

    private fun Graph<EntityID<Int>, String>.searchForOptimumBottomUp(vertexWeights: Map<EntityID<Int>, Double>, performFullSearch: Boolean, goodEnoughScore: Double): List<Pair<Graph<EntityID<Int>, String>, Double>> {
        val bestSolutions = mutableSetOf<Pair<Graph<EntityID<Int>, String>, Double>>()
        val edgeOrder = getEdgeOrdering2()
        val acceptableSize = 4..10
        val supergraphQueue = ArrayDeque(
            edgeSet().map { edgeName ->
                (AsSubgraph(this, setOf(getEdgeSource(edgeName), getEdgeTarget(edgeName)), setOf(edgeName)) to edgeOrder[edgeName]!!) to goodEnoughScore })

        while (supergraphQueue.isNotEmpty()) {
            val (graphInfo, parentScore) = supergraphQueue.removeLast()
            val (parentGraph, parentGraphInducingEdge) = graphInfo
            val derivedGraphs = if (parentScore <= goodEnoughScore && parentGraph.vertexSet().size in acceptableSize) emptyList() else
                edgeSet()
                    .filter { edgeOrder[it]!! >= parentGraphInducingEdge }
                    .filter { !parentGraph.containsEdge(it) }
                    .filter { parentGraph.containsVertex(getEdgeSource(it)) || parentGraph.containsVertex(getEdgeTarget(it)) }
                    .map { edgeName ->
                        val edgeOrdering = edgeOrder[edgeName]!!
                        val supergraph = AsSubgraph(this, parentGraph.vertexSet().union(setOf(getEdgeSource(edgeName), getEdgeTarget(edgeName))), parentGraph.edgeSet() + edgeName)

                        return@map (supergraph to edgeOrdering) to supergraph.calculateEdgeHeterogeneity()
                    }
                    .filter { parentGraph.vertexSet().size < 3 || it.second <= parentScore }

//            val bestChild = childGraphs.minByOrNull { it.first.second }

            //            if (bestChild != null) subgraphQueue.addAll(childGraphs)
//            if (bestChild != null) supergraphQueue.add(bestChild)
//            if (bestChild == null || performFullSearch) bestSolutions.add(parentGraph to parentScore)
            supergraphQueue.addAll(derivedGraphs)
            if (parentGraph.vertexSet().size + 1 in acceptableSize && derivedGraphs.isEmpty()) bestSolutions.add(parentGraph to parentScore)
        }

        return bestSolutions.sortedWith(
            compareBy(
                { it.second },
                { -it.first.vertexSet().sumOf { vertexWeights[it]!! } / it.first.vertexSet().size },
                //                    { it.first.vertexSet().size / it.first.edgeSet().size }
                { (it.first.vertexSet().size - 1) * it.first.vertexSet().size / it.first.edgeSet().size }
            ))
    }

    private fun Graph<EntityID<Int>, String>.getEdgesWithCommonVertex(edge: String): Set<String> {
        return edgesOf(getEdgeSource(edge)).union(edgesOf(getEdgeTarget(edge))) - edge
    }

    private fun Graph<EntityID<Int>, String>.getEdgeOrdering1(): Map<String, Int> {
        return edgeSet().zip(1..edgeSet().size).toMap()
    }

    private fun Graph<EntityID<Int>, String>.getEdgeOrdering2(): Map<String, Int> {
        val edges = edgeSet().toTypedArray()
        val edgeHeterogeneity = mutableMapOf<String, Double>()

        for (i in edges.indices) {
            for (j in (i + 1)..(edges.size - 1)) {
                val heterogeneity = calculateHeterogeneityBetweenTwoEdges1(edges[i], edges[j])
                edgeHeterogeneity.merge(edges[i], heterogeneity, Double::plus)
                edgeHeterogeneity.merge(edges[j], heterogeneity, Double::plus)
            }
        }
        return edgeHeterogeneity
            .toList()
            .sortedByDescending { entry -> entry.second }
            .mapIndexed { index, entry -> entry.first to index }
            .toMap()
    }

    private fun Graph<EntityID<Int>, String>.calculateVertexWeights(): Map<EntityID<Int>, Double> {
        val vertexWeights = mutableMapOf<EntityID<Int>, Double>()
        val unassignedVertices = mutableSetOf<EntityID<Int>>()

        vertexSet().forEach {
            if (outDegreeOf(it) == 0) vertexWeights[it] = 1.0 else unassignedVertices.add(it)
        }

        // simple cycles are not supported at the moment
        while(unassignedVertices.isNotEmpty()) {
            unassignedVertices.forEach { vertex ->
                val successors = outgoingEdgesOf(vertex).map { getEdgeTarget(it) }

                if (vertexWeights.keys.containsAll(successors)) {
                    vertexWeights[vertex] = successors.maxOf { vertexWeights[it]!! } + 1
                }
            }

            unassignedVertices.removeAll(vertexWeights.keys)
        }

        return vertexWeights
    }

    private fun Graph<EntityID<Int>, String>.calculateEdgeHeterogeneity(): Double {
        val edges = edgeSet().toTypedArray()
        var graphHeterogeneity = 0.0

        for (i in edges.indices) {
            for (j in (i + 1)..(edges.size - 1)) {
                graphHeterogeneity += calculateHeterogeneityBetweenTwoEdges1(edges[i], edges[j])
            }
        }

        return graphHeterogeneity / (Math.pow(edges.size.toDouble(), 3.0) / 4.0)
    }

    // Methods nr 1 and 3 give very similar results (which is expected)

    private fun Graph<EntityID<Int>, String>.calculateHeterogeneityBetweenTwoEdges1(edge1: String, edge2: String): Double {
        val outDegreeDiff = outDegreeOf(getEdgeSource(edge1)) - outDegreeOf(getEdgeSource(edge2))
        val inDegreeDiff = inDegreeOf(getEdgeTarget(edge1)) - inDegreeOf(getEdgeTarget(edge2))

        return Math.abs(outDegreeDiff).plus(Math.abs(inDegreeDiff)).toDouble()
    }

    private fun Graph<EntityID<Int>, String>.calculateHeterogeneityBetweenTwoEdges2(edge1: String, edge2: String): Double {
        val outDegreeDiff = outDegreeOf(getEdgeSource(edge1)) - outDegreeOf(getEdgeSource(edge2))
        val inDegreeDiff = inDegreeOf(getEdgeTarget(edge1)) - inDegreeOf(getEdgeTarget(edge2))

        return outDegreeDiff.plus(inDegreeDiff).toDouble()
    }

    private fun Graph<EntityID<Int>, String>.calculateHeterogeneityBetweenTwoEdges3(edge1: String, edge2: String): Double {
        val outDegreeDiff = outDegreeOf(getEdgeSource(edge1)) - outDegreeOf(getEdgeSource(edge2))
        val inDegreeDiff = inDegreeOf(getEdgeTarget(edge1)) - inDegreeOf(getEdgeTarget(edge2))

        return Math.sqrt(outDegreeDiff.toDouble()) + Math.sqrt(inDegreeDiff.toDouble())
    }

    private fun AsSubgraph<EntityID<Int>, String>.calculateHomogeneity(): Double {
        return edgeSet()
            .map {edgeName -> outDegreeOf(getEdgeSource(edgeName)).toDouble() to inDegreeOf(getEdgeTarget(edgeName)).toDouble()}
            .sumOf { (outDegree, inDegree) ->
                1 / outDegree + 1 / inDegree - 2 / sqrt(outDegree * inDegree)
            }
    }

    private fun AsSubgraph<EntityID<Int>, String>.calculateNormalizedHomogeneity(): Double {
        val vertexCount = vertexSet().count()
        val homogeneity = calculateHomogeneity()

        return if(homogeneity == 0.0) 0.0 else homogeneity / ((vertexCount * (vertexCount - 2 * sqrt(vertexCount - 1.0))) / (vertexCount - 1))
    }

    private fun discoverCaseNotionsStartingWithRoot(
        rootClass: EntityID<Int>,
        allowedClasses: Set<EntityID<Int>>,
        relationshipGraph: Graph<EntityID<Int>, String>): List<DAGBusinessPerspectiveDefinition<EntityID<Int>>> {
        return allowedClasses.powerSet().fold(mutableListOf()) { caseNotionsDefinitions, allowedClassesSubset ->
            val allowedClassesSubgraph = AsSubgraph(relationshipGraph, allowedClassesSubset)

            if (allowedClassesSubset.contains(rootClass) && GraphTests.isConnected(allowedClassesSubgraph)) {
                val shortestPaths = DijkstraShortestPath(allowedClassesSubgraph).getPaths(rootClass)
                val spanningTree = KruskalMinimumSpanningTree(allowedClassesSubgraph).spanningTree
                val caseNotionTree = mutableMapOf<EntityID<Int>, EntityID<Int>?>(rootClass to null)

                spanningTree.edges.forEach {
                    val source = allowedClassesSubgraph.getEdgeSource(it)
                    val target = allowedClassesSubgraph.getEdgeTarget(it)

                    if (shortestPaths.getPath(source).length > shortestPaths.getPath(target).length)
                        caseNotionTree[source] = target
                    else caseNotionTree[target] = source
                }

                allowedClassesSubset.powerSet().forEach { identifyingClassesSubset ->
                    val identifyingClassesSubgraph = AsSubgraph(allowedClassesSubgraph, identifyingClassesSubset)

                    if (identifyingClassesSubset.contains(rootClass) && GraphTests.isConnected(identifyingClassesSubgraph)) {
//                        caseNotionsDefinitions.add(DAGCaseNotionDefinition(caseNotionTree, identifyingClassesSubset))
                    }
                }
            }

            caseNotionsDefinitions
        }
    }

    // this is quick and dirty implementation
    private fun <T> Set<T>.powerSet(): Set<Set<T>> {
        val sets: MutableSet<Set<T>> = HashSet()
        if (this.isEmpty()) {
            sets.add(HashSet())
            return sets
        }
        val list: List<T> = ArrayList(this)
        val head = list.first()
        val rest: Set<T> = HashSet(list.subList(1, list.size))
        for (set in rest.powerSet()) {
            val newSet = mutableSetOf(head)
            newSet.addAll(set)
            sets.add(newSet)
            sets.add(set)
        }
        return sets
    }
}