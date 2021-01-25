package processm.etl.metamodel

import org.apache.commons.math3.distribution.BetaDistribution
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import processm.core.persistence.connection.DBCache

/**
 * Explores possible case notions and evaluates their potential relevance.
 *
 * @param targetDatabaseName Name of database containing meta model data.
 * @param metaModelReader Component for acquiring meta model data.
 */
class CaseNotionScorer(private val targetDatabaseName: String, private val metaModelReader: MetaModelReader) {

    private val levelOfDetailsMode = 4.0
    private val levelOfDetailsMin = 3.0
    private val levelOfDetailsMax = 6.0
    private val averageNumberOfEventsMode = 4.0
    private val averageNumberOfEventsMin = 2.0
    private val averageNumberOfEventsMax = 10.0

    /**
     * Discovers case notions consisting of the provided classes.
     *
     * @return A map containing case notions and their relevance score.
     * @see CaseNotionDefinition
     */
    fun scoreCaseNotions(caseNotions: List<CaseNotionDefinition<EntityID<Int>>>): Map<CaseNotionDefinition<EntityID<Int>>, Double>
            = transaction(DBCache.get(targetDatabaseName).database) {

        val supportScores = caseNotions
            .getSupportScores()
            .scaleValues()
//            .mapValues { 0.0 }

        val levelOfDetailScores = caseNotions
            .getLevelOfDetailScores()
            .scaleValues()
            .getProbabilityDensityFunction(levelOfDetailsMode, levelOfDetailsMin, levelOfDetailsMax)

        val averageNumberOfEventsScores = caseNotions
            .getAverageNumberOfEventsScores()
            .scaleValues()
            .getProbabilityDensityFunction(averageNumberOfEventsMode, averageNumberOfEventsMin, averageNumberOfEventsMax)
//            .mapValues { 0.0 }

        return@transaction caseNotions.map {
            it to (supportScores.getOrDefault(it, 0.0) + levelOfDetailScores.getOrDefault(it, 0.0) + averageNumberOfEventsScores.getOrDefault(it, 0.0)) / 3
        }
            .toMap()
    }

    private fun Map<CaseNotionDefinition<EntityID<Int>>, Double>.scaleValues(): Map<CaseNotionDefinition<EntityID<Int>>, Double>  {
        val min = values.minByOrNull {it} ?: 0.0
        val max = values.maxByOrNull {it} ?: 1.0
        return mapValues { (_, value) -> (value - min) / (max - min) }
    }

    private fun Map<CaseNotionDefinition<EntityID<Int>>, Double>.getProbabilityDensityFunction(mode: Double, min: Double, max: Double): Map<CaseNotionDefinition<EntityID<Int>>, Double> {
        val (alpha, beta) = getBetaDistributionParameters(mode, min, max)

        return mapValues { (_, value) -> BetaDistribution(alpha, beta).density(value) }
    }

    private fun getBetaDistributionParameters(mode: Double, min: Double, max: Double): Pair<Double, Double> {
        val scaledMode = (mode - min) / (max - min)

        if (scaledMode < 0.5) return 1 / (1 - scaledMode) to 2.0
        if (scaledMode > 0.5) return 2.0 to (1 - 4 * scaledMode) / scaledMode
        return 2.0 to 2.0
    }

    private fun List<CaseNotionDefinition<EntityID<Int>>>.getSupportScores(): Map<CaseNotionDefinition<EntityID<Int>>, Double>  {
        return map { it to getSupportScore(it) }.toMap()
    }

    private fun List<CaseNotionDefinition<EntityID<Int>>>.getLevelOfDetailScores(): Map<CaseNotionDefinition<EntityID<Int>>, Double>  {
        return map { it to getLevelOfDetailScore(it) }.toMap()
    }

    private fun List<CaseNotionDefinition<EntityID<Int>>>.getAverageNumberOfEventsScores(): Map<CaseNotionDefinition<EntityID<Int>>, Double>  {
        return map { it to getAverageNumberOfEventsScore(it) }.toMap()
    }

    // score components

    private fun getSupportScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return (getLowerBoundOfSupportScore(caseNotion) + getUpperBoundOfSupportScore(caseNotion)) / 2.0
    }

    private fun getLowerBoundOfSupportScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return getTotalNumberOfObjects(caseNotion.rootClass).toDouble()
    }

    private fun getUpperBoundOfSupportScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return caseNotion.classes.keys.map { getTotalNumberOfObjects(it) }.reduce { upperBound, element -> upperBound * (element + 1) }.toDouble()
    }

    private fun getLevelOfDetailScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return (getLowerBoundOfLevelOfDetailScore(caseNotion) + getUpperBoundOfLevelOfDetailScore(caseNotion)) / 2
    }

    private fun getLowerBoundOfLevelOfDetailScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return (getTotalNumberOfUniqueActivitiesPerObject(caseNotion.rootClass) + (getUpperBoundOfSupportScore(caseNotion) - getTotalNumberOfObjects(caseNotion.rootClass)) * getMinimumNumberOfActivitiesPerObject(caseNotion.rootClass)) /
               getUpperBoundOfSupportScore(caseNotion)
    }

    private fun getUpperBoundOfLevelOfDetailScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return caseNotion.identifyingClasses.sumOf { getMaximumNumberOfActivitiesPerObject(it) } + caseNotion.convergingClasses.sumOf { getNumberOfUniqueActivitiesPerClass(it) }.toDouble()
    }

    private fun getAverageNumberOfEventsScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return (getLowerBoundOfAverageNumberOfEventsScore(caseNotion) + getUpperBoundOfAverageNumberOfEventsScore(caseNotion)) / 2
    }

    private fun getLowerBoundOfAverageNumberOfEventsScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return (getTotalNumberOfEventsPerObject(caseNotion.rootClass) + (getUpperBoundOfSupportScore(caseNotion) - getTotalNumberOfObjects(caseNotion.rootClass)) * getMinimumNumberOfEventsPerObject(caseNotion.rootClass)) / getUpperBoundOfSupportScore(caseNotion).toDouble()
    }

    private fun getUpperBoundOfAverageNumberOfEventsScore(caseNotion: CaseNotionDefinition<EntityID<Int>>): Double {
        return caseNotion.identifyingClasses.sumOf { getMaximumNumberOfEventsPerObject(it) } + caseNotion.convergingClasses.sumOf { getNumberOfEventsPerClass(it) }.toDouble()
    }

    // metrics used for score generation
    // consider moving to MetaModelReader

    private fun getMaximumNumberOfEventsPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.objectId.count())
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.objectId)
            .maxOfOrNull { it[ObjectVersions.objectId.count()] } ?: 0
    }

    private fun getMinimumNumberOfEventsPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.objectId.count())
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.objectId)
            .minOfOrNull { it[ObjectVersions.objectId.count()] } ?: 0
    }

    // At the moment activity is defined by the <Class, CausingEventType> pair.
    // Consider defining activity as <Class, CausingEventType, AttributesNames> triple.
    private fun getMaximumNumberOfActivitiesPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.objectId.count())
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.objectId, ObjectVersions.causingEventType)
            .groupBy({ it[ObjectVersions.objectId] }, { it[ObjectVersions.objectId.count()] })
            .maxOfOrNull { it.value.sum() } ?: 0
    }

    private fun getMinimumNumberOfActivitiesPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.objectId.count())
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.objectId, ObjectVersions.causingEventType)
            .groupBy({ it[ObjectVersions.objectId] }, { it[ObjectVersions.objectId.count()] })
            .minOfOrNull { it.value.sum() } ?: 0
    }

    private fun getNumberOfEventsPerClass(classId: EntityID<Int>): Long {
        return ObjectVersions
            .select { ObjectVersions.classId eq classId }
            .count()
    }

    private fun getNumberOfUniqueActivitiesPerClass(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.classId, ObjectVersions.causingEventType)
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.classId, ObjectVersions.causingEventType)
            .count()
    }

    private fun getTotalNumberOfEventsPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .select { ObjectVersions.classId eq classId }
            .count()
    }

    private fun getTotalNumberOfUniqueActivitiesPerObject(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId, ObjectVersions.causingEventType)
            .select { ObjectVersions.classId eq classId }
            .groupBy(ObjectVersions.objectId, ObjectVersions.causingEventType)
            .count()
    }

    private fun getTotalNumberOfObjects(classId: EntityID<Int>): Long {
        return ObjectVersions
            .slice(ObjectVersions.objectId)
            .select { ObjectVersions.classId eq classId }
            .withDistinct()
            .count()
    }
}