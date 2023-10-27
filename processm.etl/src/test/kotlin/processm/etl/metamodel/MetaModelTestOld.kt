//package processm.etl.metamodel
//
//import org.jetbrains.exposed.dao.id.EntityID
//import org.jetbrains.exposed.sql.transactions.transaction
//import org.jgrapht.graph.DefaultDirectedGraph
//import org.jgroups.util.UUID
//import org.junit.jupiter.api.AfterAll
//import org.junit.jupiter.api.BeforeAll
//import org.junit.jupiter.api.TestInstance
//import processm.core.helpers.toLocalDateTime
//import processm.core.persistence.connection.DBCache
//import processm.dbmodels.models.*
//import java.time.Instant
//import kotlin.test.AfterTest
//import kotlin.test.BeforeTest
//import kotlin.test.Test
//import kotlin.test.assertEquals
//
///**
// * Test based on https://link.springer.com/article/10.1007/s10115-019-01430-6, in particular Fig. 9, Tables 1 and 2
// */
//@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//class MetaModelTest {
//
//    private lateinit var eket: Class
//    private lateinit var eban: Class
//    private lateinit var ekko: Class
//    private lateinit var ekpo: Class
//    private lateinit var graph: DefaultDirectedGraph<EntityID<Int>, String>
//    private lateinit var metaModel: MetaModel
//    private lateinit var av1: ObjectVersion
//    private lateinit var av2: ObjectVersion
//    private lateinit var av3: ObjectVersion
//    private lateinit var bv1: ObjectVersion
//    private lateinit var bv2: ObjectVersion
//    private lateinit var bv3: ObjectVersion
//    private lateinit var cv1: ObjectVersion
//    private lateinit var cv2: ObjectVersion
//    private lateinit var cv3: ObjectVersion
//    private lateinit var dv1: ObjectVersion
//    private lateinit var dv2: ObjectVersion
//    private lateinit var dv3: ObjectVersion
//    private lateinit var dv4: ObjectVersion
//    private lateinit var eket2eban: Relationship
//    private lateinit var ekko2eban: Relationship
//    private lateinit var ekpo2ekko: Relationship
//    private val temporaryDB = UUID.randomUUID().toString()
//
//    @BeforeTest
//    fun setup() {
//        transaction(DBCache.get(temporaryDB).database) {
//            // DataModel from Fig 9 and Table 1 in https://doi.org/10.1007/s10115-019-01430-6
//            val dataModel = DataModel.new {
//                name = "test"
//                versionDate = Instant.now().toLocalDateTime()
//            }
//            val metaModelId = dataModel.id.value
//            eket = Class.new {
//                this.dataModel = dataModel
//                this.name = "EKET"
//            }
//            eban = Class.new {
//                this.dataModel = dataModel
//                this.name = "EBAN"
//            }
//            ekko = Class.new {
//                this.dataModel = dataModel
//                this.name = "EKKO"
//            }
//            ekpo = Class.new {
//                this.dataModel = dataModel
//                this.name = "EKPO"
//            }
//            eket2eban = Relationship.new {
//                name = "eket2eban"
//                sourceClass = eket
//                targetClass = eban
//                referencingAttributesName = AttributesName.new {
//                    name = "attr1"
//                    type = "type1"
//                    attributeClass = eban
//                    isReferencingAttribute = true
//                }
//            }
//            ekko2eban = Relationship.new {
//                name = "ekko2eban"
//                sourceClass = ekko
//                targetClass = eban
//                referencingAttributesName = AttributesName.new {
//                    name = "attr2"
//                    type = "type2"
//                    attributeClass = eban
//                    isReferencingAttribute = true
//                }
//            }
//            ekpo2ekko = Relationship.new {
//                name = "ekpo2ekko"
//                sourceClass = ekpo
//                targetClass = ekko
//                referencingAttributesName = AttributesName.new {
//                    name = "attr3"
//                    type = "type3"
//                    attributeClass = ekko
//                    isReferencingAttribute = true
//                }
//            }
//            av1 = ObjectVersion.new { versionClass = eket; originalId = "a1" }
//            av2 = ObjectVersion.new { versionClass = eket; originalId = "a1" }
//            av3 = ObjectVersion.new { versionClass = eket; originalId = "a2" }
//            bv1 = ObjectVersion.new { versionClass = eban; originalId = "b1" }
//            bv2 = ObjectVersion.new { versionClass = eban; originalId = "b1" }
//            bv3 = ObjectVersion.new { versionClass = eban; originalId = "b2" }
//            cv1 = ObjectVersion.new { versionClass = ekko; originalId = "c1" }
//            cv2 = ObjectVersion.new { versionClass = ekko; originalId = "c2" }
//            cv3 = ObjectVersion.new { versionClass = ekko; originalId = "c3" }
//            dv1 = ObjectVersion.new { versionClass = ekpo; originalId = "d1" }
//            dv2 = ObjectVersion.new { versionClass = ekpo; originalId = "d2" }
//            dv3 = ObjectVersion.new { versionClass = ekpo; originalId = "d3" }
//            dv4 = ObjectVersion.new { versionClass = ekpo; originalId = "d4" }
//            Relation.new { relationship = eket2eban; sourceObjectVersion = av1;targetObjectVersion = bv1 }
//            Relation.new { relationship = eket2eban; sourceObjectVersion = av2;targetObjectVersion = bv2 }
//            Relation.new { relationship = eket2eban; sourceObjectVersion = av3;targetObjectVersion = bv3 }
//            Relation.new { relationship = ekko2eban; sourceObjectVersion = cv1;targetObjectVersion = bv2 }
//            Relation.new { relationship = ekko2eban; sourceObjectVersion = cv2;targetObjectVersion = bv2 }
//            Relation.new { relationship = ekko2eban; sourceObjectVersion = cv3;targetObjectVersion = bv3 }
//            Relation.new { relationship = ekpo2ekko; sourceObjectVersion = dv1;targetObjectVersion = cv1 }
//            Relation.new { relationship = ekpo2ekko; sourceObjectVersion = dv2;targetObjectVersion = cv1 }
//            Relation.new { relationship = ekpo2ekko; sourceObjectVersion = dv3;targetObjectVersion = cv2 }
//            Relation.new { relationship = ekpo2ekko; sourceObjectVersion = dv4;targetObjectVersion = cv3 }
//            graph = DefaultDirectedGraph<EntityID<Int>, String>(String::class.java)
//            graph.addVertex(eket.id)
//            graph.addVertex(eban.id)
//            graph.addVertex(ekko.id)
//            graph.addVertex(ekpo.id)
//            graph.addEdge(eket.id, eban.id, "eket->eban")
//            graph.addEdge(ekko.id, eban.id, "ekko->eban")
//            graph.addEdge(ekpo.id, ekko.id, "ekpo->ekko")
//
//            val metaModelReader = MetaModelReader(metaModelId)
//            val metaModelAppender = MetaModelAppender(metaModelReader)
//            metaModel = MetaModel(temporaryDB, metaModelReader, metaModelAppender)
//        }
//    }
//
//
//    @AfterTest
//    fun cleanup() {
//        DBCache.get(temporaryDB).close()
//        DBCache.getMainDBPool().getConnection().use {
//            it.prepareStatement("drop database \"$temporaryDB\" ").execute()
//        }
//    }
//
//    @Test
//    fun `Table 2 row a`() {
//        with(DAGBusinessPerspectiveDefinition(graph) { setOf(eket.id, eban.id) }) {
//            val flatTraces = metaModel.buildTracesForBusinessPerspective(this).toList()
//            assertEquals(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    cv2.id.value,
//                    dv1.id.value,
//                    dv2.id.value,
//                    dv3.id.value
//                ),
//                flatTraces[0]
//            )
//            assertEquals(setOf(av3.id.value, bv3.id.value, cv3.id.value, dv4.id.value), flatTraces[1])
//        }
//    }
//
//    @Test
//    fun `Table 2 row b`() {
//        with(DAGBusinessPerspectiveDefinition(graph) { setOf(eket.id, eban.id, ekko.id) }) {
//            val flatTraces = metaModel.buildTracesForBusinessPerspective(this).toSet()
//            assertEquals(
//                setOf(
//                    setOf(
//                        av1.id.value,
//                        av2.id.value,
//                        bv1.id.value,
//                        bv2.id.value,
//                        cv1.id.value,
//                        dv1.id.value,
//                        dv2.id.value
//                    ),
//                    setOf(av1.id.value, av2.id.value, bv1.id.value, bv2.id.value, cv2.id.value, dv3.id.value),
//                    setOf(av3.id.value, bv3.id.value, cv3.id.value, dv4.id.value)
//                ),
//                flatTraces
//            )
//        }
//    }
//
//    @Test
//    fun `Table 2 row c`() {
//        with(DAGBusinessPerspectiveDefinition(graph)) {
//            val flatTraces = metaModel.buildTracesForBusinessPerspective(this).toSet()
//            assertEquals(
//                setOf(
//                    setOf(av1.id.value, av2.id.value, bv1.id.value, bv2.id.value, cv1.id.value, dv1.id.value),
//                    setOf(av1.id.value, av2.id.value, bv1.id.value, bv2.id.value, cv1.id.value, dv2.id.value),
//                    setOf(av1.id.value, av2.id.value, bv1.id.value, bv2.id.value, cv2.id.value, dv3.id.value),
//                    setOf(av3.id.value, bv3.id.value, cv3.id.value, dv4.id.value)
//                ), flatTraces
//            )
//        }
//    }
//
//    @Test
//    fun `hardcoded SQL query for Table 2 row b`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = """
//                WITH case_identifiers(ci) AS (
//                SELECT DISTINCT ARRAY_CAT(ARRAY[ARRAY[c1.class_id::TEXT, c1.object_id], ARRAY[c2.class_id::TEXT, c2.object_id], ARRAY[c3.class_id::TEXT, c3.object_id]],
//                ARRAY_AGG(ARRAY[c4.class_id::TEXT, c4.object_id]))
//                FROM
//                object_versions AS c1,
//                object_versions AS c2,
//                object_versions AS c3,
//                relations as r12,
//                relations as r13
//                ,object_versions c4, relations as r34
//                WHERE
//                c1.class_id = ${eban.id.value} AND c2.class_id = ${eket.id.value} AND c3.class_id = ${ekko.id.value} AND
//                r12.source_object_version_id = c2.id AND r12.target_object_version_id = c1.id AND
//                r13.source_object_version_id = c3.id AND r13.target_object_version_id = c1.id
//                AND c4.class_id = ${ekpo.id.value} AND r34.source_object_version_id = c4.id AND r34.target_object_version_id = c3.id
//                GROUP BY c1.class_id::TEXT, c1.object_id, c2.class_id::TEXT, c2.object_id, c3.class_id::TEXT, c3.object_id
//                )
//                SELECT case_identifiers.ci, ARRAY_AGG(ovs.id)
//                FROM  case_identifiers, object_versions AS ovs
//                WHERE
//                ARRAY[ovs.class_id::TEXT, ovs.object_id] = ANY((SELECT * FROM unnest_2d_1d(case_identifiers.ci)))
//                GROUP BY case_identifiers.ci
//            """.trimIndent()
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Pair<Set<Pair<Int, String>>, Set<Int>>>()
//                while (rs.next()) {
//                    val caseIdentifier = (rs.getArray(1).array as Array<Array<String>>).mapTo(HashSet()) {
//                        assertEquals(2, it.size)
//                        it[0].toInt() to it[1]
//                    }
//                    val trace = (rs.getArray(2).array as Array<Int>).toSet()
//                    result.add(caseIdentifier to trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    eket.id.value to "a1",
//                    eban.id.value to "b1",
//                    ekko.id.value to "c1",
//                    ekpo.id.value to "d1",
//                    ekpo.id.value to "d2"
//                ) to
//                        setOf(
//                            av1.id.value,
//                            av2.id.value,
//                            bv1.id.value,
//                            bv2.id.value,
//                            cv1.id.value,
//                            dv1.id.value,
//                            dv2.id.value
//                        ),
//                setOf(
//                    eket.id.value to "a1",
//                    eban.id.value to "b1",
//                    ekko.id.value to "c2",
//                    ekpo.id.value to "d3"
//                ) to
//                        setOf(
//                            av1.id.value,
//                            av2.id.value,
//                            bv1.id.value,
//                            bv2.id.value,
//                            cv2.id.value,
//                            dv3.id.value
//                        ),
//                setOf(
//                    eket.id.value to "a2",
//                    eban.id.value to "b2",
//                    ekko.id.value to "c3",
//                    ekpo.id.value to "d4"
//                ) to
//                        setOf(
//                            av3.id.value,
//                            bv3.id.value,
//                            cv3.id.value,
//                            dv4.id.value
//                        ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//    @Test
//    fun `hardcoded SQL query v2 for Table 2 row b`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = """
//                WITH case_identifiers(ci) AS (
//                    SELECT DISTINCT ARRAY_CAT(ARRAY[ROW(c1.class_id, c1.object_id)::remote_object_identifier,
//                    ROW(c2.class_id, c2.object_id)::remote_object_identifier, ROW(c3.class_id, c3.object_id)::remote_object_identifier],
//                    ARRAY_AGG(ROW(c4.class_id, c4.object_id)::remote_object_identifier))
//                    FROM
//                    object_versions AS c1,
//                    object_versions AS c2,
//                    object_versions AS c3,
//                    relations as r12,
//                    relations as r13
//                    ,object_versions c4, relations as r34
//                    WHERE
//                    c1.class_id = ${eban.id.value} AND c2.class_id = ${eket.id.value} AND c3.class_id = ${ekko.id.value} AND
//                    r12.source_object_version_id = c2.id AND r12.target_object_version_id = c1.id AND
//                    r13.source_object_version_id = c3.id AND r13.target_object_version_id = c1.id
//                    AND c4.class_id = ${ekpo.id.value} AND r34.source_object_version_id = c4.id AND r34.target_object_version_id = c3.id
//                    GROUP BY c1.class_id, c1.object_id, c2.class_id, c2.object_id, c3.class_id, c3.object_id
//                )
//                SELECT ARRAY_AGG(ovs.id)
//                FROM  case_identifiers, object_versions AS ovs
//                WHERE
//                ROW(ovs.class_id, ovs.object_id)::remote_object_identifier = ANY((SELECT * FROM unnest(case_identifiers.ci)))
//                GROUP BY case_identifiers.ci
//            """.trimIndent()
//            // JDBC returns objects of user-defined types as strings (via the PGobject type) for the user to parse. Not gonna bother with testing the case identifiers
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Set<Int>>()
//                while (rs.next()) {
//                    val trace = (rs.getArray(1).array as Array<Int>).toSet()
//                    result.add(trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    dv1.id.value,
//                    dv2.id.value
//                ),
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv2.id.value,
//                    dv3.id.value
//                ),
//                setOf(
//                    av3.id.value,
//                    bv3.id.value,
//                    cv3.id.value,
//                    dv4.id.value
//                ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//    @Test
//    fun `generated SQL query v2 for Table 2 row c`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = DAGBusinessPerspectiveDefinition(graph).generateSQLquery()
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Set<Int>>()
//                while (rs.next()) {
//                    val trace = (rs.getArray(1).array as Array<Int>).toSet()
//                    result.add(trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    dv1.id.value
//                ),
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    dv2.id.value
//                ),
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv2.id.value,
//                    dv3.id.value
//                ),
//                setOf(
//                    av3.id.value,
//                    bv3.id.value,
//                    cv3.id.value,
//                    dv4.id.value
//                ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//    @Test
//    fun `generated SQL query v2 for Table 2 row b`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = DAGBusinessPerspectiveDefinition(graph) { setOf(eket.id, eban.id, ekko.id) }.generateSQLquery()
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Set<Int>>()
//                while (rs.next()) {
//                    val trace = (rs.getArray(1).array as Array<Int>).toSet()
//                    result.add(trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    dv1.id.value,
//                    dv2.id.value
//                ),
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv2.id.value,
//                    dv3.id.value
//                ),
//                setOf(
//                    av3.id.value,
//                    bv3.id.value,
//                    cv3.id.value,
//                    dv4.id.value
//                ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//    @Test
//    fun `generated SQL query v2 for Table 2 row a`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = DAGBusinessPerspectiveDefinition(graph) { setOf(eket.id, eban.id) }.generateSQLquery()
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Set<Int>>()
//                while (rs.next()) {
//                    val trace = (rs.getArray(1).array as Array<Int>).toSet()
//                    result.add(trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    cv2.id.value,
//                    dv1.id.value,
//                    dv2.id.value,
//                    dv3.id.value
//                ),
//                setOf(
//                    av3.id.value,
//                    bv3.id.value,
//                    cv3.id.value,
//                    dv4.id.value
//                ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//
//    @Test
//    fun `hardcoded SQL query v2 for Table 2 row a`() {
//        DBCache.get(temporaryDB).getConnection().use { connection ->
//            val query = """
//                WITH case_identifiers(ci) AS (
//                    SELECT DISTINCT ARRAY[ROW(c1.class_id, c1.object_id)::remote_object_identifier,
//                    ROW(c2.class_id, c2.object_id)::remote_object_identifier] ||
//                    ARRAY_AGG(ROW(c4.class_id, c4.object_id)::remote_object_identifier) || ARRAY_AGG(ROW(c3.class_id, c3.object_id)::remote_object_identifier)
//                    FROM
//                    object_versions AS c1,
//                    object_versions AS c2,
//                    object_versions AS c3,
//                    relations as r12,
//                    relations as r13
//                    ,object_versions c4, relations as r34
//                    WHERE
//                    c1.class_id = ${eban.id.value} AND c2.class_id = ${eket.id.value} AND c3.class_id = ${ekko.id.value} AND
//                    r12.source_object_version_id = c2.id AND r12.target_object_version_id = c1.id AND
//                    r13.source_object_version_id = c3.id AND r13.target_object_version_id = c1.id
//                    AND c4.class_id = ${ekpo.id.value} AND r34.source_object_version_id = c4.id AND r34.target_object_version_id = c3.id
//                    GROUP BY c1.class_id, c1.object_id, c2.class_id, c2.object_id
//                )
//                SELECT ARRAY_AGG(ovs.id)
//                FROM  case_identifiers, object_versions AS ovs
//                WHERE
//                ROW(ovs.class_id, ovs.object_id)::remote_object_identifier = ANY((SELECT * FROM unnest(case_identifiers.ci)))
//                GROUP BY case_identifiers.ci
//            """.trimIndent()
//            // JDBC returns objects of user-defined types as strings (via the PGobject type) for the user to parse. Not gonna bother with testing the case identifiers
//            val result = connection.prepareStatement(query).executeQuery().use { rs ->
//                val result = HashSet<Set<Int>>()
//                while (rs.next()) {
//                    val trace = (rs.getArray(1).array as Array<Int>).toSet()
//                    result.add(trace)
//                }
//                return@use result
//            }
//            val expected = setOf(
//                setOf(
//                    av1.id.value,
//                    av2.id.value,
//                    bv1.id.value,
//                    bv2.id.value,
//                    cv1.id.value,
//                    cv2.id.value,
//                    dv1.id.value,
//                    dv2.id.value,
//                    dv3.id.value
//                ),
//                setOf(
//                    av3.id.value,
//                    bv3.id.value,
//                    cv3.id.value,
//                    dv4.id.value
//                ),
//            )
//            assertEquals(expected, result)
//        }
//    }
//
//    @Test
//    fun blah() {
////        transaction(DBCache.get(temporaryDB).database) {
////            val dv2b = ObjectVersion.new { versionClass = ekpo; originalId = "d2" }
////            Relation.new { relationship = ekpo2ekko; sourceObjectVersion = dv2b;targetObjectVersion = cv3 }
////        }
//        DBCache.get(temporaryDB).getConnection().use { connection ->
////            val query = """
////                SELECT c1.id, c1.object_id, c2.id, c2.object_id, c3.id, c3.object_id, c4.id, c4.object_id
////                FROM object_versions AS c1,
////                relations as r12,
////                object_versions AS c2
////                ,relations as r13,
////                object_versions AS c3
////                ,relations as r34,
////                object_versions AS c4
////                WHERE c1.class_id = ${eban.id.value} AND c1.object_id = 'b1'
////                AND r12.source_object_version_id = c2.id AND c2.class_id = ${eket.id.value} AND r12.target_object_version_id = c1.id
////                AND r13.source_object_version_id = c3.id AND c3.class_id = ${ekko.id.value} AND r13.target_object_version_id = c1.id
////                AND r34.source_object_version_id = c4.id AND c4.class_id = ${ekpo.id.value} AND r34.target_object_version_id = r13.source_object_version_id
////            """.trimIndent()
//            /**
//             * TODO:
//             * 1. Patrzymy tylko na najnowsza wersje obiektu - najlepiej byloby miec identyfikator zamiast go wyszukiwac. Docelowo trzeba zmienic strukture bazy danych i przechowywac tylko najnowsza wersje obiektu
//             * 2. Kolumny, które wybieramy zależą od widoku, który przyjmujemy
//             */
//            val query = """
//                SELECT c1.id, c1.object_id, c2.id, c2.object_id, c3.id, c3.object_id--, c4.id, c4.object_id
//                FROM object_versions AS c1,
//                relations as r12,
//                object_versions AS c2
//                ,relations as r13,
//                object_versions AS c3
//                ,relations as r34,
//                object_versions AS c4
//                WHERE c1.class_id = ${eban.id.value}
//                AND r12.source_object_version_id = c2.id AND c2.class_id = ${eket.id.value} AND r12.target_object_version_id = c1.id
//                AND r13.source_object_version_id = c3.id AND c3.class_id = ${ekko.id.value} AND r13.target_object_version_id = c1.id
//                AND r34.source_object_version_id = c4.id AND c4.class_id = ${ekpo.id.value} AND r34.target_object_version_id = r13.source_object_version_id
//                AND c4.id = (SELECT MAX(c.id) FROM object_versions as c WHERE c.object_id = 'd2')
//            """.trimIndent()
//            println("+++++++++++++++++++")
//            connection.prepareStatement(query).executeQuery().use { rs ->
//                val nColumns = rs.metaData.columnCount
//                while (rs.next()) {
//                    println((1..nColumns).map { rs.getObject(it) })
//                }
//            }
//            println("-------------------")
////            val query = """
////                WITH case_identifiers(ci) AS (
////                    SELECT DISTINCT ARRAY[ROW(c1.class_id, c1.object_id)::remote_object_identifier,
////                    ROW(c2.class_id, c2.object_id)::remote_object_identifier] ||
////                    ARRAY_AGG(ROW(c4.class_id, c4.object_id)::remote_object_identifier) || ARRAY_AGG(ROW(c3.class_id, c3.object_id)::remote_object_identifier)
////                    FROM
////                    object_versions AS c1,
////                    object_versions AS c2,
////                    object_versions AS c3,
////                    relations as r12,
////                    relations as r13
////                    ,object_versions c4, relations as r34
////                    WHERE
////                    c1.class_id = ${eban.id.value} AND c2.class_id = ${eket.id.value} AND c3.class_id = ${ekko.id.value} AND
////                    r12.source_object_version_id = c2.id AND r12.target_object_version_id = c1.id AND
////                    r13.source_object_version_id = c3.id AND r13.target_object_version_id = c1.id
////                    AND c4.class_id = ${ekpo.id.value} AND r34.source_object_version_id = c4.id AND r34.target_object_version_id = c3.id
////                    GROUP BY c1.class_id, c1.object_id, c2.class_id, c2.object_id
////                )
////                SELECT ARRAY_AGG(ovs.id)
////                FROM  case_identifiers, object_versions AS ovs
////                WHERE
////                ROW(ovs.class_id, ovs.object_id)::remote_object_identifier = ANY((SELECT * FROM unnest(case_identifiers.ci)))
////                GROUP BY case_identifiers.ci
////            """.trimIndent()
////            val expected = setOf(
////                setOf(
////                    av1.id.value,
////                    av2.id.value,
////                    bv1.id.value,
////                    bv2.id.value,
////                    cv1.id.value,
////                    cv2.id.value,
////                    dv1.id.value,
////                    dv2.id.value,
////                    dv3.id.value
////                ),
////                setOf(
////                    av3.id.value,
////                    bv3.id.value,
////                    cv3.id.value,
////                    dv4.id.value
////                ),
////            )
////            assertEquals(expected, result)
//        }
//    }
//}