package processm.conformance.models.alignments.hmtests

import org.junit.jupiter.api.AfterAll
import processm.conformance.measures.RangeFitness
import processm.conformance.models.alignments.petrinet.DecompositionAligner
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.toPetriNet
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.test.Test

@InMemoryXESProcessing
class CoSeLoG_WABO_2 {

    companion object {
        val pool = ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            Runtime.getRuntime().availableProcessors(),
            60,
            TimeUnit.SECONDS,
            LinkedBlockingQueue()
        )


        @JvmStatic
        @AfterAll
        fun cleanUp() {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    @Test
    fun test() {
        val log =
            javaClass.classLoader.getResourceAsStream("train_CoSeLoG_WABO_2.xes.gz_25_1_2.xes.gz").use { rawStream ->
                GZIPInputStream(rawStream).use { xml ->
                    HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(xml)).first()
                }
            }
        val aligner = DecompositionAligner(model.toPetriNet(), pool = pool)
        val fitness = RangeFitness(aligner, 100, TimeUnit.MILLISECONDS)(log)
        println(fitness)
    }

    /**
     * Created by OnlineHeuristicMiner using `train_CoSeLoG_WABO_2.xes.gz_25_1_2.xes.gz` as the train log.
     */
    val model = causalnet {
        start = Node("start", "", true)
        end = Node("end", "", true)
        Node("01_HOOFD_370") + Node("01_HOOFD_516") or Node("01_HOOFD_370") + Node("01_HOOFD_520") or Node("01_HOOFD_530") + Node(
            "01_HOOFD_370"
        ) + Node("01_HOOFD_520") join Node("01_BB_540")
        Node("01_BB_540") splits Node("01_HOOFD_810") + Node("01_BB_770") or Node("01_BB_770")
        Node("01_HOOFD_530") joins Node("01_BB_630")
        Node("01_BB_630") splits Node("01_BB_730")
        Node("01_BB_630") joins Node("01_BB_730")
        Node("01_BB_730") splits Node("01_BB_770")
        Node("01_BB_540") or Node("01_HOOFD_810") + Node("01_BB_540") or Node("01_HOOFD_810") + Node("01_HOOFD_805") + Node(
            "01_BB_540"
        ) or Node("01_BB_730") join Node("01_BB_770")
        Node("01_BB_770") splits Node("01_HOOFD_810") or Node("01_HOOFD_820") or Node("01_HOOFD_820") + Node("01_HOOFD_810") or Node(
            "01_BB_775"
        ) + Node("01_HOOFD_810") + Node("01_HOOFD_820") or Node("01_BB_775") + Node("01_HOOFD_820") or Node("01_HOOFD_790")
        Node("01_HOOFD_810") + Node("01_BB_770") or Node("01_BB_770") join Node("01_BB_775")
        Node("01_BB_775") splits Node("01_HOOFD_815") + Node("01_HOOFD_814") or Node("01_HOOFD_810") + Node("01_HOOFD_815") + Node(
            "01_HOOFD_814"
        )
        start or Node("01_HOOFD_030_2") + start join Node("01_HOOFD_010")
        Node("01_HOOFD_010") splits Node("01_HOOFD_015") or Node("01_HOOFD_015") + Node("01_HOOFD_030_2") or Node("01_HOOFD_015") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_011") or Node("01_HOOFD_015") + Node("01_HOOFD_011")
        Node("01_HOOFD_010") joins Node("01_HOOFD_011")
        Node("01_HOOFD_011") splits Node("01_HOOFD_020")
        Node("01_HOOFD_030_2") + Node("01_HOOFD_010") or Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") + Node(
            "01_HOOFD_010"
        ) or Node("01_HOOFD_010") or Node("01_HOOFD_065_2") + Node("01_HOOFD_030_2") + Node("01_HOOFD_010") or Node("01_HOOFD_030_2") + Node(
            "01_HOOFD_020"
        ) + Node("01_HOOFD_010") or Node("01_HOOFD_065_2") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("01_HOOFD_055") + Node(
            "05_EIND_010"
        ) + Node("01_HOOFD_010") or Node("01_HOOFD_065_2") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("01_HOOFD_120") + Node(
            "01_HOOFD_010"
        ) or Node("01_HOOFD_020") + Node("05_EIND_010") + Node("01_HOOFD_010") join Node("01_HOOFD_015")
        Node("01_HOOFD_015") splits Node("01_HOOFD_030_1") or Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") or Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_020") + Node("03_GBH_005") + Node("01_HOOFD_110") or Node("01_HOOFD_030_1") + Node("01_HOOFD_020") + Node(
            "05_EIND_010"
        ) + Node("01_HOOFD_110") or Node("01_HOOFD_030_1") + Node("01_HOOFD_020") + Node("05_EIND_010") + Node("03_GBH_005") + Node(
            "01_HOOFD_110"
        ) or Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") + Node("03_GBH_005") + Node(
            "01_HOOFD_110"
        ) or Node("01_HOOFD_030_1") + Node("05_EIND_010") + Node("03_GBH_005") or Node("01_HOOFD_110") or Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_020") + Node("05_EIND_010") + Node("03_GBH_005")
        Node("01_HOOFD_065_2") + Node("01_HOOFD_530") or Node("01_HOOFD_015") + Node("05_EIND_010") or Node("01_HOOFD_011") or Node(
            "01_HOOFD_015"
        ) or Node("01_HOOFD_065_2") or Node("01_HOOFD_015") + Node("14_VRIJ_010") + Node("05_EIND_010") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_015"
        ) join Node("01_HOOFD_020")
        Node("01_HOOFD_020") splits Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_040") or Node("01_HOOFD_015") + Node(
            "01_HOOFD_030_1"
        ) + Node("05_EIND_010") + Node("03_GBH_005") or Node("01_HOOFD_030_1") + Node("05_EIND_010") + Node("01_HOOFD_040") or Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_030_2") + Node("14_VRIJ_010") + Node("03_GBH_005") + Node("01_HOOFD_040") or Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("14_VRIJ_010") + Node("05_EIND_010") + Node("03_GBH_005") + Node("01_HOOFD_040") or Node("01_HOOFD_015") + Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_030_2") + Node("05_EIND_010") + Node("03_GBH_005") or Node("01_HOOFD_030_1") + Node("05_EIND_010") + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_040") or Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node("14_VRIJ_010") + Node("05_EIND_010") + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_040")
        Node("01_HOOFD_015") + Node("01_HOOFD_020") + Node("05_EIND_010") or Node("01_HOOFD_015") + Node("01_HOOFD_060") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_020") or Node("01_HOOFD_015") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") or Node(
            "01_HOOFD_015"
        ) + Node("01_HOOFD_020") or Node("01_HOOFD_015") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") or Node("01_HOOFD_060") + Node(
            "14_VRIJ_010"
        ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") or Node("01_HOOFD_015") + Node("14_VRIJ_010") + Node(
            "01_HOOFD_020"
        ) + Node("05_EIND_010") or Node("01_HOOFD_015") + Node("14_VRIJ_010") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") or Node(
            "01_HOOFD_015"
        ) + Node("14_VRIJ_010") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") join Node("01_HOOFD_030_1")
        Node("01_HOOFD_030_1") splits Node("01_HOOFD_065_1") or Node("01_HOOFD_030_2") + Node("01_HOOFD_040") or Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_040") or Node("01_HOOFD_065_1") + Node("01_HOOFD_040") or Node("02_DRZ_010") or Node("01_HOOFD_065_1") + Node(
            "02_DRZ_010"
        ) or Node("01_HOOFD_065_1") + Node("02_DRZ_010") + Node("01_HOOFD_030_2")
        Node("01_HOOFD_065_2") + start or start + Node("01_HOOFD_010") or Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_020"
        ) + Node("05_EIND_010") + start + Node("03_GBH_005") + Node("01_HOOFD_010") or Node("01_HOOFD_015") + Node("01_HOOFD_020") + start + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_010") or Node("01_HOOFD_020") + Node("05_EIND_010") + start + Node("03_GBH_005") + Node("01_HOOFD_010") or Node(
            "01_HOOFD_015"
        ) + Node("01_HOOFD_020") + Node("05_EIND_010") + start + Node("03_GBH_005") + Node("01_HOOFD_010") or Node("01_HOOFD_015") + Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_020") + start + Node("01_HOOFD_010") or start join Node("01_HOOFD_030_2")
        Node("01_HOOFD_030_2") splits Node("01_HOOFD_065_2") + Node("01_HOOFD_015") + Node("02_DRZ_010") + Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_010"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node("01_HOOFD_040") + Node("01_HOOFD_010") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node("14_VRIJ_010") + Node("01_HOOFD_040") + Node("01_HOOFD_101") or Node(
            "01_HOOFD_065_2"
        ) + Node("02_DRZ_010") + Node("01_HOOFD_015") + Node("01_HOOFD_030_1") or Node("02_DRZ_010") + Node("01_HOOFD_030_1") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_030_1") + Node("01_HOOFD_040") + Node("01_HOOFD_101") or Node("01_HOOFD_040") or Node("01_HOOFD_065_2") + Node(
            "02_DRZ_010"
        ) or Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node("01_HOOFD_040") + Node("01_HOOFD_010") or Node("01_HOOFD_065_2") + Node(
            "14_VRIJ_010"
        ) + Node("01_HOOFD_040") + Node("01_HOOFD_101") or Node("01_HOOFD_065_2") + Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_040"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_040") + Node("01_HOOFD_101")
        Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") + Node("03_GBH_005") or Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_020") or Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") or Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_020") + Node("03_GBH_005") or Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node("05_EIND_010") + Node(
            "03_GBH_005"
        ) join Node("01_HOOFD_040")
        Node("01_HOOFD_040") splits Node("01_HOOFD_060") + Node("01_HOOFD_050") or Node("01_HOOFD_060")
        Node("01_HOOFD_060") + Node("05_EIND_010") + Node("01_HOOFD_040") or Node("01_HOOFD_060") + Node("05_EIND_010") + Node(
            "01_HOOFD_040"
        ) + Node("01_HOOFD_101") or Node("01_HOOFD_060") + Node("01_HOOFD_540") + Node("05_EIND_010") + Node("01_HOOFD_040") or Node(
            "01_HOOFD_060"
        ) + Node("01_HOOFD_540") + Node("01_HOOFD_040") + Node("01_HOOFD_100") or Node("05_EIND_010") + Node("01_HOOFD_040") + Node(
            "01_HOOFD_101"
        ) or Node("01_HOOFD_060") + Node("01_HOOFD_540") + Node("05_EIND_010") + Node("01_HOOFD_040") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_060"
        ) + Node("01_HOOFD_540") + Node("01_HOOFD_040") or Node("01_HOOFD_040") join Node("01_HOOFD_050")
        Node("01_HOOFD_050") splits Node("01_HOOFD_065_1") + Node("01_HOOFD_060") or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node(
            "01_HOOFD_055"
        ) or Node("01_HOOFD_065_1") + Node("01_HOOFD_055") or Node("01_HOOFD_130") or Node("01_HOOFD_065_1")
        Node("01_HOOFD_065_1") + Node("01_HOOFD_050") or Node("01_HOOFD_050") join Node("01_HOOFD_055")
        Node("01_HOOFD_055") splits Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_060"
        ) or Node("01_HOOFD_015")
        Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node("01_HOOFD_055") + Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node(
            "01_HOOFD_040"
        ) join Node("01_HOOFD_060")
        Node("01_HOOFD_060") splits Node("01_HOOFD_065_1") + end + Node("01_HOOFD_050") + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_065_1"
        ) + end + Node("01_HOOFD_101") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + end + Node("01_HOOFD_050") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("07_OPS_010") + end + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_065_1"
        ) + Node("07_OPS_010") + end + Node("01_HOOFD_101") or end or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node(
            "07_OPS_010"
        ) + end + Node("01_HOOFD_050") + Node("01_HOOFD_101") or Node("01_HOOFD_065_1") + Node("07_OPS_010") + Node("01_HOOFD_030_1") + end or Node(
            "01_HOOFD_065_1"
        ) + Node("07_OPS_010") + Node("01_HOOFD_030_1") + end + Node("01_HOOFD_101")
        Node("04_BPT_005") joins Node("01_HOOFD_061")
        Node("01_HOOFD_061") splits end or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + end
        Node("01_HOOFD_060") + Node("01_HOOFD_030_1") or Node("01_HOOFD_060") + Node("01_HOOFD_030_1") + Node("01_HOOFD_050") or Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_061") or Node("01_HOOFD_060") + Node("01_HOOFD_030_1") + Node("01_HOOFD_055") + Node("01_HOOFD_050") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_030_1") + Node("01_HOOFD_061") or Node("01_HOOFD_065_2") + Node("01_HOOFD_060") + Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_050"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_060") + Node("01_HOOFD_030_1") + Node("01_HOOFD_055") + Node("01_HOOFD_050") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_060") + Node("01_HOOFD_030_1") join Node("01_HOOFD_065_1")
        Node("01_HOOFD_065_1") splits Node("01_HOOFD_180") + Node("07_OPS_010") + Node("01_HOOFD_055") + Node("01_HOOFD_110") or Node(
            "01_HOOFD_180"
        ) + Node("07_OPS_010") + Node("01_HOOFD_110") or Node("01_HOOFD_180") + Node("01_HOOFD_110") or Node("14_VRIJ_010") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_180") + Node("07_OPS_010") + Node("01_HOOFD_110") or Node("01_HOOFD_180") + Node("01_HOOFD_540") + Node(
            "01_HOOFD_110"
        ) or Node("01_HOOFD_180") + Node("01_HOOFD_110_1") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("01_HOOFD_110_1") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_180") + Node("01_HOOFD_540") + Node("01_HOOFD_110")
        Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + start + Node("01_HOOFD_100") or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_055") + start or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node(
            "05_EIND_010"
        ) + start + Node("01_HOOFD_101") or Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node("05_EIND_010") + start or Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node("01_HOOFD_055") + Node("05_EIND_010") + start or Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("05_EIND_010") + start + Node("01_HOOFD_061") or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node(
            "05_EIND_010"
        ) + start or Node("01_HOOFD_030_2") + Node("05_EIND_010") + start + Node("01_HOOFD_061") or Node("01_HOOFD_030_2") + start + Node(
            "01_HOOFD_101"
        ) or Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node("01_HOOFD_055") + Node("05_EIND_010") + start or start or Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_180") + Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node("05_EIND_010") + start + Node("01_HOOFD_100") or Node(
            "01_HOOFD_060"
        ) + Node("01_HOOFD_030_2") + start or Node("01_HOOFD_030_2") + start join Node("01_HOOFD_065_2")
        Node("01_HOOFD_065_2") splits Node("01_HOOFD_065_1") + Node("01_HOOFD_110_1") or Node("01_HOOFD_490_3") + Node("01_HOOFD_110") + Node(
            "01_HOOFD_120"
        ) or Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node("01_HOOFD_020") + Node(
            "01_HOOFD_490_3"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_490_3") + Node("01_HOOFD_100") or Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_015"
        ) + Node("01_HOOFD_020") + Node("01_HOOFD_490_3") + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_490_3") + Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_110_1") or Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_110"
        ) + Node("01_HOOFD_120") or Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node(
            "01_HOOFD_490_3"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node("01_HOOFD_065_1") + Node("01_HOOFD_490_3") + Node(
            "01_HOOFD_110"
        ) + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node("01_HOOFD_100")
        Node("01_HOOFD_110_2") + Node("01_HOOFD_490_2") or Node("01_HOOFD_110_2") join Node("01_HOOFD_099")
        Node("01_HOOFD_099") splits Node("08_AWB45_005") + Node("01_HOOFD_490_4") or Node("01_HOOFD_490_4")
        Node("01_HOOFD_060") + Node("01_HOOFD_540") + Node("01_HOOFD_110") or Node("01_HOOFD_180") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_065_2"
        ) + Node("07_OPS_010") + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("01_HOOFD_060") + Node(
            "01_HOOFD_540"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node("01_HOOFD_060") + Node("01_HOOFD_540") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_180") + Node("01_HOOFD_100") or Node("01_HOOFD_180") + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node("01_HOOFD_540") + Node("01_HOOFD_100") or Node("01_HOOFD_060") + Node(
            "01_HOOFD_540"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node("01_HOOFD_060") + Node("01_HOOFD_540") + Node(
            "01_HOOFD_110"
        ) + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_060"
        ) join Node("01_HOOFD_100")
        Node("01_HOOFD_100") splits Node("06_VD_010") + Node("05_EIND_010") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_180") + Node("05_EIND_010") + Node("01_HOOFD_050") + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_100") or Node("01_HOOFD_180") + Node("06_VD_010") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node(
            "01_HOOFD_120"
        ) or Node("01_HOOFD_180") + Node("05_EIND_010") + Node("01_HOOFD_100") or Node("01_HOOFD_180") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_180") + Node("05_EIND_010") + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_180") + Node("06_VD_010") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node("01_HOOFD_050") + Node(
            "01_HOOFD_120"
        ) or Node("06_VD_010")
        Node("01_HOOFD_030_2") or Node("07_OPS_010") + Node("01_HOOFD_060") + Node("06_VD_010") + Node("01_HOOFD_030_2") + Node(
            "01_HOOFD_490_1"
        ) or Node("01_HOOFD_060") + Node("01_HOOFD_030_2") or Node("01_HOOFD_060") + Node("01_HOOFD_030_2") + Node("01_HOOFD_520") + Node(
            "01_HOOFD_490_1"
        ) or Node("07_OPS_010") + Node("01_HOOFD_060") + Node("01_HOOFD_030_2") or Node("07_OPS_010") + Node("01_HOOFD_060") + Node(
            "06_VD_010"
        ) + Node("01_HOOFD_030_2") join Node("01_HOOFD_101")
        Node("01_HOOFD_101") splits Node("01_HOOFD_530") + end or end or Node("01_HOOFD_190") + Node("01_HOOFD_180") + end or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("06_VD_010") + Node("01_HOOFD_530") + end + Node("01_HOOFD_050") or Node(
            "01_HOOFD_190"
        ) + Node("06_VD_010") + Node("01_HOOFD_530") + end or Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("06_VD_010") + Node(
            "01_HOOFD_530"
        ) + end
        Node("01_HOOFD_065_2") + Node("01_HOOFD_180") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node(
            "01_HOOFD_130"
        ) + Node("01_HOOFD_100") or Node("08_AWB45_170") + Node("01_HOOFD_180") + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_015") + Node("01_HOOFD_100") or Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node("01_HOOFD_130") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node("01_HOOFD_130") + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_015") + Node("01_HOOFD_120") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_015") + Node(
            "01_HOOFD_130"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_015") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node(
            "01_HOOFD_015"
        ) + Node("01_HOOFD_130") + Node("01_HOOFD_120") + Node("01_HOOFD_100") join Node("01_HOOFD_110")
        Node("01_HOOFD_110") splits Node("06_VD_010") + Node("01_HOOFD_180") or Node("06_VD_010") + Node("01_HOOFD_180") + Node(
            "07_OPS_010"
        ) or Node("06_VD_010") + Node("01_HOOFD_180") + Node("01_HOOFD_100") or Node("01_HOOFD_180") + Node("06_VD_010") + Node(
            "01_HOOFD_120"
        ) or Node("01_HOOFD_120") or Node("01_HOOFD_180") + Node("01_HOOFD_120") or Node("06_VD_010") + Node("07_OPS_010") + Node(
            "01_HOOFD_180"
        ) + Node("01_HOOFD_120") or Node("07_OPS_010") + Node("01_HOOFD_180") + Node("01_HOOFD_120") or Node("06_VD_010") + Node(
            "01_HOOFD_180"
        ) + Node("01_HOOFD_130") + Node("01_HOOFD_120") or Node("06_VD_010") + Node("01_HOOFD_180") + Node("01_HOOFD_120") + Node(
            "01_HOOFD_100"
        )
        Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_180") or Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") join Node(
            "01_HOOFD_110_1"
        )
        Node("01_HOOFD_110_1") splits Node("01_HOOFD_180") + Node("01_HOOFD_110_2") or Node("01_HOOFD_110_2")
        Node("01_HOOFD_180") + Node("01_HOOFD_110_1") or Node("01_HOOFD_110_1") join Node("01_HOOFD_110_2")
        Node("01_HOOFD_110_2") splits Node("01_HOOFD_180") + Node("08_AWB45_005") or Node("08_AWB45_005") + Node("01_HOOFD_099")
        Node("01_HOOFD_065_2") or Node("06_VD_010") + Node("01_HOOFD_110") or Node("01_HOOFD_065_2") + Node("01_HOOFD_130") or Node(
            "01_HOOFD_065_2"
        ) + Node("06_VD_010") + Node("01_HOOFD_110") or Node("01_HOOFD_130") + Node("01_HOOFD_110") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_130") + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node("01_HOOFD_130") + Node("01_HOOFD_110") or Node(
            "01_HOOFD_130"
        ) + Node("01_HOOFD_110") or Node("01_HOOFD_065_2") + Node("01_HOOFD_110") or Node("01_HOOFD_065_2") + Node("06_VD_010") + Node(
            "01_HOOFD_130"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node("01_HOOFD_130") + Node("01_HOOFD_110") + Node(
            "01_HOOFD_100"
        ) join Node("01_HOOFD_120")
        Node("01_HOOFD_120") splits Node("01_HOOFD_015") or Node("01_HOOFD_180") + Node("01_HOOFD_110") + Node("01_HOOFD_100") or Node(
            "01_HOOFD_180"
        ) + Node("01_HOOFD_110") or Node("01_HOOFD_180") + Node("01_HOOFD_100") or Node("01_HOOFD_180")
        Node("01_HOOFD_110") or Node("01_HOOFD_050") join Node("01_HOOFD_130")
        Node("01_HOOFD_130") splits Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("03_GBH_005") + Node("01_HOOFD_120")
        Node("01_HOOFD_065_1") + Node("06_VD_010") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node(
            "01_HOOFD_100"
        ) or Node("01_HOOFD_065_1") + Node("01_HOOFD_110_1") + Node("05_EIND_010") or Node("01_HOOFD_065_1") + Node("05_EIND_010") + Node(
            "01_HOOFD_110"
        ) + Node("01_HOOFD_120") + Node("01_HOOFD_101") or Node("01_HOOFD_065_1") + Node("06_VD_010") + Node("05_EIND_010") + Node(
            "01_HOOFD_110"
        ) + Node("01_HOOFD_120") + Node("01_HOOFD_101") or Node("01_HOOFD_065_1") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node(
            "01_HOOFD_120"
        ) or Node("01_HOOFD_065_1") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node("01_HOOFD_100") or Node(
            "06_VD_010"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") + Node("01_HOOFD_101") or Node("01_HOOFD_065_1") + Node("01_HOOFD_110") + Node(
            "01_HOOFD_120"
        ) + Node("01_HOOFD_100") or Node("01_HOOFD_065_1") + Node("01_HOOFD_110_2") + Node("01_HOOFD_110_1") + Node("05_EIND_010") or Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_120") or Node("01_HOOFD_065_1") + Node("05_EIND_010") or Node("06_VD_010") + Node(
            "01_HOOFD_101"
        ) join Node("01_HOOFD_180")
        Node("01_HOOFD_180") splits Node("01_HOOFD_100") or Node("01_HOOFD_065_2") + Node("01_HOOFD_100") or Node("01_HOOFD_190") + Node(
            "07_OPS_010"
        ) + Node("05_EIND_010") or Node("01_HOOFD_190") + Node("07_OPS_010") or Node("05_EIND_010") or Node("08_AWB45_005") + Node(
            "01_HOOFD_110_2"
        ) or Node("01_HOOFD_190") or Node("01_HOOFD_065_2") + Node("05_EIND_010") + Node("01_HOOFD_100") or Node("01_HOOFD_190") + Node(
            "05_EIND_010"
        ) + Node("01_HOOFD_110") or Node("08_AWB45_005") or Node("01_HOOFD_190") + Node("07_OPS_010") + Node("01_HOOFD_110") or Node(
            "08_AWB45_005"
        ) + Node("01_HOOFD_110_2") + Node("01_HOOFD_110_1") or Node("07_OPS_010") or Node("05_EIND_010") + Node("01_HOOFD_100")
        Node("06_VD_010") + Node("01_HOOFD_180") or Node("01_HOOFD_180") + Node("05_EIND_010") + Node("01_HOOFD_101") or Node(
            "06_VD_010"
        ) + Node("01_HOOFD_180") + Node("01_HOOFD_101") or Node("06_VD_010") + Node("01_HOOFD_180") + Node("05_EIND_010") or Node(
            "01_HOOFD_180"
        ) + Node("05_EIND_010") or Node("06_VD_010") + Node("01_HOOFD_180") + Node("05_EIND_010") + Node("01_HOOFD_101") join Node(
            "01_HOOFD_190"
        )
        Node("01_HOOFD_190") splits Node("08_AWB45_010") or Node("01_HOOFD_200") + Node("01_HOOFD_195")
        Node("01_HOOFD_200") + Node("01_HOOFD_190") + Node("01_HOOFD_260") or Node("01_HOOFD_190") or Node("01_HOOFD_200") join Node(
            "01_HOOFD_195"
        )
        Node("01_HOOFD_195") splits Node("01_HOOFD_200") + end + Node("01_HOOFD_375") or end + Node("01_HOOFD_250_1") + Node(
            "01_HOOFD_375"
        ) or end + Node("01_HOOFD_375") or end
        Node("08_AWB45_005") joins Node("01_HOOFD_196")
        Node("01_HOOFD_196") splits Node("01_HOOFD_200")
        Node("01_HOOFD_195") or Node("01_HOOFD_196") or Node("01_HOOFD_190") + Node("06_VD_010") + Node("01_HOOFD_195") or Node(
            "01_HOOFD_190"
        ) + Node("01_HOOFD_195") or Node("06_VD_010") or Node("01_HOOFD_190") + Node("06_VD_010") join Node("01_HOOFD_200")
        Node("01_HOOFD_200") splits Node("01_HOOFD_260") + Node("01_HOOFD_250") + Node("15_NGV_010") or Node("01_HOOFD_260") + Node(
            "01_HOOFD_250"
        ) + Node("01_HOOFD_195") + Node("15_NGV_010") or Node("01_HOOFD_260") + Node("01_HOOFD_270") + Node("01_HOOFD_250") or Node(
            "01_HOOFD_195"
        ) or Node("01_HOOFD_260") + Node("01_HOOFD_250")
        Node("01_HOOFD_200") + Node("01_HOOFD_260") or Node("01_HOOFD_200") or Node("01_HOOFD_200") + Node("01_HOOFD_270") or Node(
            "01_HOOFD_200"
        ) + Node("01_HOOFD_480") join Node("01_HOOFD_250")
        Node("01_HOOFD_250") splits Node("01_HOOFD_330") + Node("01_HOOFD_510_2") + Node("01_HOOFD_260") + Node("01_HOOFD_490_1") + Node(
            "01_HOOFD_270"
        ) or Node("01_HOOFD_330") + Node("01_HOOFD_260") + Node("01_HOOFD_490_1") + Node("15_NGV_010") or Node("01_HOOFD_260") + Node(
            "01_HOOFD_490_1"
        ) or Node("01_HOOFD_330") + Node("01_HOOFD_510_2") + Node("01_HOOFD_260") + Node("01_HOOFD_490_1") + Node("15_NGV_010") or Node(
            "01_HOOFD_330"
        ) + Node("01_HOOFD_510_2") + Node("01_HOOFD_260") + Node("01_HOOFD_490_1") or Node("01_HOOFD_330") + Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_490_1"
        ) + Node("15_NGV_010")
        Node("01_HOOFD_195") joins Node("01_HOOFD_250_1")
        Node("01_HOOFD_250_1") splits Node("01_HOOFD_250_2")
        Node("01_HOOFD_250_1") joins Node("01_HOOFD_250_2")
        Node("01_HOOFD_250_2") splits Node("01_HOOFD_330")
        Node("01_HOOFD_200") + Node("01_HOOFD_495") + Node("05_EIND_010") + Node("01_HOOFD_250") + Node("15_NGV_010") or Node(
            "01_HOOFD_200"
        ) + Node("05_EIND_010") + Node("01_HOOFD_250") or Node("01_HOOFD_200") + Node("05_EIND_010") + Node("01_HOOFD_270") + Node(
            "01_HOOFD_250"
        ) or Node("01_HOOFD_200") + Node("05_EIND_010") or Node("01_HOOFD_200") + Node("05_EIND_010") + Node("01_HOOFD_250") + Node(
            "15_NGV_010"
        ) join Node("01_HOOFD_260")
        Node("01_HOOFD_260") splits Node("01_HOOFD_330") + Node("15_NGV_010") or Node("01_HOOFD_195") or Node("01_HOOFD_330") or Node(
            "01_HOOFD_330"
        ) + Node("01_HOOFD_250") + Node("15_NGV_010")
        Node("01_HOOFD_200") or Node("01_HOOFD_200") + Node("01_HOOFD_250") join Node("01_HOOFD_270")
        Node("01_HOOFD_270") splits Node("01_HOOFD_260") or Node("01_HOOFD_260") + Node("01_HOOFD_250")
        Node("15_NGV_010") or Node("01_HOOFD_260") + Node("01_HOOFD_250") + Node("15_NGV_010") or Node("01_HOOFD_260") + Node(
            "01_HOOFD_250"
        ) or Node("01_HOOFD_250_2") join Node("01_HOOFD_330")
        Node("01_HOOFD_330") splits Node("01_HOOFD_350_2") + Node("09_AH_I_010") + Node("01_HOOFD_350_1") or Node("09_AH_I_010") or Node(
            "09_AH_I_010"
        ) + Node("15_NGV_010")
        Node("01_HOOFD_330") or Node("01_HOOFD_330") + Node("01_HOOFD_350_2") join Node("01_HOOFD_350_1")
        Node("01_HOOFD_350_1") splits Node("09_AH_I_010") or Node("01_HOOFD_350_2") + Node("09_AH_I_010")
        Node("01_HOOFD_330") or Node("01_HOOFD_330") + Node("01_HOOFD_350_1") join Node("01_HOOFD_350_2")
        Node("01_HOOFD_350_2") splits Node("09_AH_I_010") + Node("01_HOOFD_350_1") or Node("09_AH_I_010")
        Node("09_AH_I_010") + Node("01_HOOFD_375") or Node("01_HOOFD_510_1") + Node("01_HOOFD_490_1") + Node("09_AH_I_010") + Node(
            "01_HOOFD_375"
        ) or Node("09_AH_I_010") or Node("01_HOOFD_490_1") + Node("09_AH_I_010") + Node("01_HOOFD_375") join Node("01_HOOFD_370")
        Node("01_HOOFD_370") splits Node("01_HOOFD_380") + Node("01_HOOFD_490_1") + Node("01_BB_540") + Node("01_HOOFD_490_2") + Node(
            "01_HOOFD_375"
        ) or Node("07_OPS_010") + Node("01_HOOFD_380") + Node("01_HOOFD_490_1") + Node("01_BB_540") + Node("01_HOOFD_490_2") or Node(
            "01_HOOFD_380"
        ) + Node("01_HOOFD_490_1") + Node("01_HOOFD_490_2") or Node("01_BB_540") + Node("01_HOOFD_490_2") or Node("01_HOOFD_380") + Node(
            "01_HOOFD_490_1"
        ) or Node("01_BB_540") + Node("01_HOOFD_490_1") + Node("01_HOOFD_490_2") or Node("01_BB_540") or Node("01_HOOFD_380") + Node(
            "01_HOOFD_490_1"
        ) + Node("01_BB_540") + Node("01_HOOFD_490_2")
        Node("01_HOOFD_370") + Node("09_AH_I_010") + Node("01_HOOFD_195") or Node("09_AH_I_010") + Node("01_HOOFD_195") or Node(
            "01_HOOFD_480"
        ) + Node("09_AH_I_010") + Node("01_HOOFD_490_1") + Node("01_HOOFD_195") or Node("01_HOOFD_480") + Node("09_AH_I_010") + Node(
            "01_HOOFD_195"
        ) join Node("01_HOOFD_375")
        Node("01_HOOFD_375") splits Node("01_HOOFD_370") + Node("01_HOOFD_490_1") or Node("01_HOOFD_380") + Node("01_HOOFD_370") + Node(
            "01_HOOFD_490_1"
        ) + Node("01_HOOFD_520") or Node("01_HOOFD_380") + Node("01_HOOFD_370") + Node("01_HOOFD_490_1") or Node("01_HOOFD_380") + Node(
            "01_HOOFD_490_1"
        ) + Node("01_HOOFD_520") or Node("01_HOOFD_370") or Node("01_HOOFD_370") + Node("01_HOOFD_520")
        Node("06_VD_010") + Node("09_AH_I_010") or Node("01_HOOFD_370") + Node("09_AH_I_010") + Node("01_HOOFD_375") or Node(
            "09_AH_I_010"
        ) or Node("06_VD_010") + Node("01_HOOFD_370") + Node("09_AH_I_010") + Node("01_HOOFD_375") join Node("01_HOOFD_380")
        Node("01_HOOFD_380") splits Node("01_HOOFD_440_1") or Node("01_HOOFD_430")
        Node("01_HOOFD_380") joins Node("01_HOOFD_430")
        Node("01_HOOFD_430") splits Node("01_HOOFD_470") or Node("11_AH_II_010")
        Node("01_HOOFD_380") joins Node("01_HOOFD_440_1")
        Node("01_HOOFD_440_1") splits Node("01_HOOFD_445")
        Node("01_HOOFD_445") joins Node("01_HOOFD_440_2")
        Node("01_HOOFD_440_2") splits Node("01_HOOFD_446")
        Node("01_HOOFD_440_1") joins Node("01_HOOFD_445")
        Node("01_HOOFD_445") splits Node("01_HOOFD_440_2")
        Node("01_HOOFD_440_2") joins Node("01_HOOFD_446")
        Node("01_HOOFD_446") splits Node("01_HOOFD_460")
        Node("01_HOOFD_446") joins Node("01_HOOFD_460")
        Node("01_HOOFD_460") splits Node("01_HOOFD_465")
        Node("01_HOOFD_460") joins Node("01_HOOFD_465")
        Node("01_HOOFD_465") splits Node("10_UOV_010")
        Node("01_HOOFD_430") or Node("10_UOV_180") join Node("01_HOOFD_470")
        Node("01_HOOFD_470") splits Node("01_HOOFD_480") + Node("01_HOOFD_490_1") or Node("01_HOOFD_490_1")
        Node("11_AH_II_010") or Node("01_HOOFD_470") join Node("01_HOOFD_480")
        Node("01_HOOFD_480") splits Node("01_HOOFD_490_1") or Node("01_HOOFD_490_1") + Node("01_HOOFD_375") or Node("01_HOOFD_490_1") + Node(
            "01_HOOFD_250"
        ) + Node("01_HOOFD_375")
        Node("01_HOOFD_480") + Node("01_HOOFD_375") or Node("01_HOOFD_480") + Node("01_HOOFD_370") + Node("01_HOOFD_375") or Node(
            "01_HOOFD_480"
        ) or Node("01_HOOFD_470") + Node("01_HOOFD_370") + Node("01_HOOFD_250") + Node("01_HOOFD_375") or Node("01_HOOFD_470") + Node(
            "01_HOOFD_480"
        ) + Node("01_HOOFD_250") or Node("01_HOOFD_480") + Node("01_HOOFD_370") + Node("01_HOOFD_250") + Node("01_HOOFD_375") or Node(
            "01_HOOFD_470"
        ) + Node("01_HOOFD_480") + Node("01_HOOFD_370") + Node("01_HOOFD_250") + Node("01_HOOFD_375") join Node("01_HOOFD_490_1")
        Node("01_HOOFD_490_1") splits Node("01_HOOFD_370") + Node("01_HOOFD_490_2") + Node("01_HOOFD_375") or Node("01_HOOFD_491") + Node(
            "01_HOOFD_370"
        ) + Node("01_HOOFD_490_2") or Node("01_HOOFD_491") + Node("01_HOOFD_370") + Node("01_HOOFD_490_2") + Node("01_HOOFD_375") or Node(
            "01_HOOFD_101"
        ) or Node("01_HOOFD_491") + Node("01_HOOFD_490_2") or Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_490_2") + Node(
            "01_HOOFD_490_3"
        ) + Node("01_HOOFD_101")
        Node("01_HOOFD_370") + Node("01_HOOFD_490_1") or Node("01_HOOFD_530") + Node("01_HOOFD_491") + Node("01_HOOFD_370") + Node(
            "01_HOOFD_490_1"
        ) or Node("01_HOOFD_370") + Node("01_HOOFD_490_1") + Node("01_HOOFD_490_3") or Node("01_HOOFD_530") + Node("01_HOOFD_490_1") + Node(
            "01_HOOFD_490_3"
        ) or Node("01_HOOFD_491") + Node("01_HOOFD_370") + Node("01_HOOFD_490_1") join Node("01_HOOFD_490_2")
        Node("01_HOOFD_490_2") splits Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_495") + Node(
            "01_HOOFD_510_1"
        ) + Node("01_HOOFD_510_2") or Node("01_HOOFD_495") + Node("01_HOOFD_510_1") or Node("01_HOOFD_510_1") or Node("01_HOOFD_495") + Node(
            "01_HOOFD_510_1"
        ) + Node("01_HOOFD_510_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_495") + Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_099"
        ) + Node("01_HOOFD_490_4") or Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") or Node("01_HOOFD_495") + Node("01_HOOFD_510_1") + Node(
            "01_HOOFD_510_2"
        ) + Node("01_HOOFD_490_4")
        Node("01_HOOFD_065_2") or Node("01_HOOFD_065_2") + Node("01_HOOFD_490_1") or Node("01_HOOFD_065_2") + Node("01_HOOFD_490_1") + Node(
            "01_HOOFD_490_2"
        ) join Node("01_HOOFD_490_3")
        Node("01_HOOFD_490_3") splits Node("01_HOOFD_495") + Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") or Node("01_HOOFD_495") + Node(
            "01_HOOFD_510_1"
        ) + Node("01_HOOFD_510_2") + Node("01_HOOFD_490_2") or Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node("01_HOOFD_490_2") or Node(
            "01_HOOFD_510_1"
        ) + Node("01_HOOFD_510_2")
        Node("01_HOOFD_099") + Node("01_HOOFD_490_2") join Node("01_HOOFD_490_4")
        Node("01_HOOFD_490_4") splits Node("01_HOOFD_491")
        Node("01_HOOFD_491") joins Node("01_HOOFD_490_5")
        Node("01_HOOFD_490_5") splits Node("01_HOOFD_495")
        Node("01_HOOFD_490_1") + Node("01_HOOFD_490_4") or Node("01_HOOFD_490_1") join Node("01_HOOFD_491")
        Node("01_HOOFD_491") splits Node("01_HOOFD_510_2") + Node("01_HOOFD_490_2") or Node("01_HOOFD_490_2") or Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_490_5"
        )
        Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_490_2") or Node("01_HOOFD_490_2") + Node("01_HOOFD_490_5") join Node(
            "01_HOOFD_495"
        )
        Node("01_HOOFD_495") splits Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") or Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_505"
        ) or Node("01_HOOFD_510_1") + Node("01_HOOFD_505") or Node("01_HOOFD_510_1") + Node("01_HOOFD_260") or Node("01_HOOFD_510_1") + Node(
            "01_HOOFD_510_2"
        ) + Node("01_HOOFD_500")
        Node("01_HOOFD_495") joins Node("01_HOOFD_500")
        Node("01_HOOFD_500") splits Node("01_HOOFD_510_2")
        Node("01_HOOFD_495") or Node("01_HOOFD_495") + Node("01_HOOFD_520") join Node("01_HOOFD_505")
        Node("01_HOOFD_505") splits Node("01_HOOFD_530")
        Node("01_HOOFD_510_2") + Node("01_HOOFD_530") + Node("01_HOOFD_490_2") or Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_490_2"
        ) or Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node("01_HOOFD_530") + Node("01_HOOFD_490_2") or Node("01_HOOFD_495") + Node(
            "01_HOOFD_510_2"
        ) + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_510_2") + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node(
            "01_HOOFD_495"
        ) + Node("01_HOOFD_510_2") + Node("01_HOOFD_530") + Node("01_HOOFD_520") + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node(
            "01_HOOFD_495"
        ) + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") or Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_530"
        ) + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_3") join Node("01_HOOFD_510_1")
        Node("01_HOOFD_510_1") splits Node("01_HOOFD_510_3") + Node("01_HOOFD_510_4") or Node("01_HOOFD_510_2") + Node("01_HOOFD_520") or Node(
            "01_HOOFD_520"
        ) or Node("01_HOOFD_370")
        Node("01_HOOFD_495") + Node("01_HOOFD_490_2") + Node("01_HOOFD_250") + Node("01_HOOFD_490_3") or Node("01_HOOFD_490_2") + Node(
            "01_HOOFD_250"
        ) + Node("01_HOOFD_490_3") or Node("01_HOOFD_490_3") or Node("01_HOOFD_495") + Node("01_HOOFD_491") + Node("01_HOOFD_490_2") + Node(
            "01_HOOFD_500"
        ) or Node("01_HOOFD_495") + Node("01_HOOFD_491") + Node("01_HOOFD_490_2") + Node("01_HOOFD_250") or Node("01_HOOFD_495") + Node(
            "01_HOOFD_510_1"
        ) + Node("01_HOOFD_490_2") + Node("01_HOOFD_250") + Node("01_HOOFD_490_3") or Node("01_HOOFD_510_1") + Node("01_HOOFD_490_2") + Node(
            "01_HOOFD_250"
        ) + Node("01_HOOFD_490_3") or Node("01_HOOFD_250") or Node("01_HOOFD_491") + Node("01_HOOFD_250") join Node("01_HOOFD_510_2")
        Node("01_HOOFD_510_2") splits Node("01_HOOFD_510_1") + Node("01_HOOFD_530") + Node("01_HOOFD_520") or Node("01_HOOFD_530") + Node(
            "01_HOOFD_520"
        ) or Node("01_HOOFD_510_1") + Node("01_HOOFD_530") + Node("05_EIND_010") + Node("01_HOOFD_520") or Node("01_HOOFD_510_1")
        Node("01_HOOFD_510_1") + Node("01_HOOFD_515") or Node("01_HOOFD_510_1") join Node("01_HOOFD_510_3")
        Node("01_HOOFD_510_3") splits Node("01_HOOFD_519") + Node("01_HOOFD_510_4") + Node("01_HOOFD_515") or Node("01_HOOFD_519")
        Node("01_HOOFD_510_1") + Node("01_HOOFD_510_3") + Node("01_HOOFD_515") or Node("01_HOOFD_510_1") + Node("01_HOOFD_510_3") or Node(
            "01_HOOFD_510_1"
        ) join Node("01_HOOFD_510_4")
        Node("01_HOOFD_510_4") splits Node("01_HOOFD_519") or Node("01_HOOFD_519") + Node("01_HOOFD_515")
        Node("01_HOOFD_510_3") or Node("01_HOOFD_510_4") or Node("01_HOOFD_510_3") + Node("01_HOOFD_510_4") join Node("01_HOOFD_515")
        Node("01_HOOFD_515") splits Node("01_HOOFD_519") or Node("01_HOOFD_519") + Node("01_HOOFD_510_4") or Node("01_HOOFD_519") + Node(
            "01_HOOFD_510_3"
        )
        Node("01_HOOFD_519") joins Node("01_HOOFD_516")
        Node("01_HOOFD_516") splits Node("01_BB_540")
        Node("01_HOOFD_510_3") + Node("01_HOOFD_510_4") + Node("01_HOOFD_515") join Node("01_HOOFD_519")
        Node("01_HOOFD_519") splits Node("01_HOOFD_516")
        Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node("01_HOOFD_375") or Node("01_HOOFD_510_2") + Node("01_HOOFD_530") + Node(
            "01_HOOFD_375"
        ) or Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node("01_HOOFD_530") + Node("01_HOOFD_375") join Node("01_HOOFD_520")
        Node("01_HOOFD_520") splits Node("01_BB_540") or Node("01_HOOFD_530") + Node("01_BB_540") or Node("01_HOOFD_510_1") + Node(
            "01_BB_540"
        ) or Node("01_HOOFD_530") + Node("01_BB_540") + Node("01_HOOFD_101") or Node("01_HOOFD_530") + Node("01_HOOFD_505") or Node(
            "01_HOOFD_530"
        ) + Node("01_BB_540") + Node("01_HOOFD_505")
        Node("01_HOOFD_510_2") + Node("01_HOOFD_520") + Node("01_HOOFD_101") or Node("01_HOOFD_510_2") + Node("01_HOOFD_520") + Node(
            "01_HOOFD_505"
        ) + Node("01_HOOFD_101") or Node("01_HOOFD_510_2") + Node("01_HOOFD_505") + Node("01_HOOFD_101") or Node("01_HOOFD_510_2") or Node(
            "01_HOOFD_510_2"
        ) + Node("01_HOOFD_520") or Node("01_HOOFD_510_2") + Node("01_HOOFD_800") + Node("01_HOOFD_520") join Node("01_HOOFD_530")
        Node("01_HOOFD_530") splits Node("01_HOOFD_510_1") + end + Node("01_BB_540") + Node("01_HOOFD_520") + Node("01_HOOFD_490_2") or end or Node(
            "01_HOOFD_510_1"
        ) + end + Node("01_BB_540") + Node("01_HOOFD_520") or end + Node("01_BB_540") or end + Node("01_BB_540") + Node(
            "01_HOOFD_520"
        ) or end + Node("01_BB_630") or Node("01_HOOFD_510_1") + Node("01_HOOFD_020") + end + Node("01_BB_540") + Node("01_HOOFD_520") + Node(
            "01_HOOFD_490_2"
        )
        Node("01_HOOFD_065_1") joins Node("01_HOOFD_540")
        Node("01_HOOFD_540") splits Node("05_EIND_010") + Node("01_HOOFD_050") + Node("01_HOOFD_100")
        Node("01_BB_770") joins Node("01_HOOFD_790")
        Node("01_HOOFD_790") splits Node("01_HOOFD_800") + Node("01_HOOFD_805") or Node("01_HOOFD_800")
        Node("01_HOOFD_790") + Node("01_HOOFD_805") or Node("01_HOOFD_790") join Node("01_HOOFD_800")
        Node("01_HOOFD_800") splits end or Node("01_HOOFD_530") + end
        Node("01_HOOFD_790") or Node("01_HOOFD_810") join Node("01_HOOFD_805")
        Node("01_HOOFD_805") splits Node("01_HOOFD_800") or Node("01_HOOFD_820") + Node("01_BB_770") or Node("01_HOOFD_820")
        Node("01_BB_770") + Node("01_BB_540") or Node("01_BB_775") + Node("01_BB_770") + Node("01_BB_540") or Node("01_BB_540") join Node(
            "01_HOOFD_810"
        )
        Node("01_HOOFD_810") splits Node("01_HOOFD_820") + end or Node("01_BB_775") + Node("01_HOOFD_820") + end + Node(
            "01_HOOFD_814"
        ) or Node("01_BB_775") + Node("01_HOOFD_820") + Node("01_BB_770") + end + Node("01_HOOFD_814") or Node("01_HOOFD_820") + end + Node(
            "01_HOOFD_814"
        ) or end or Node("01_HOOFD_820") + Node("01_HOOFD_805") + end or Node("01_HOOFD_820") + Node("01_BB_770") + Node(
            "01_HOOFD_805"
        ) + end
        Node("01_BB_775") + Node("01_HOOFD_810") or Node("01_BB_775") + Node("01_HOOFD_810") + Node("01_HOOFD_815") join Node(
            "01_HOOFD_814"
        )
        Node("01_HOOFD_814") splits Node("01_HOOFD_820") or Node("01_HOOFD_820") + Node("01_HOOFD_815")
        Node("01_BB_775") + Node("01_HOOFD_814") or Node("01_BB_775") join Node("01_HOOFD_815")
        Node("01_HOOFD_815") splits Node("01_HOOFD_820") + Node("01_HOOFD_814") or Node("01_HOOFD_820")
        Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_HOOFD_815") + Node("01_HOOFD_814") or Node("01_HOOFD_810") + Node(
            "01_BB_770"
        ) + Node("01_HOOFD_805") or Node("01_HOOFD_810") + Node("01_BB_770") join Node("01_HOOFD_820")
        Node("01_HOOFD_820") splits end
        Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") join Node("02_DRZ_010")
        Node("02_DRZ_010") splits Node("04_BPT_005")
        Node("14_VRIJ_010") + Node("01_HOOFD_020") or Node("01_HOOFD_015") + Node("01_HOOFD_020") + Node("05_EIND_010") or Node(
            "01_HOOFD_015"
        ) + Node("01_HOOFD_020") + Node("01_HOOFD_130") or Node("01_HOOFD_015") + Node("14_VRIJ_010") + Node("01_HOOFD_020") + Node(
            "05_EIND_010"
        ) or Node("01_HOOFD_015") + Node("05_EIND_010") or Node("01_HOOFD_015") + Node("01_HOOFD_020") or Node("01_HOOFD_020") or Node(
            "01_HOOFD_015"
        ) + Node("14_VRIJ_010") + Node("01_HOOFD_020") join Node("03_GBH_005")
        Node("03_GBH_005") splits Node("01_HOOFD_030_2") + Node("14_VRIJ_010") + Node("01_HOOFD_040") or Node("01_HOOFD_030_2") + Node(
            "05_EIND_010"
        ) or Node("01_HOOFD_040") or Node("01_HOOFD_030_2") + Node("05_EIND_010") + Node("01_HOOFD_040") or Node("05_EIND_010") or Node(
            "05_EIND_010"
        ) + Node("01_HOOFD_040") or Node("01_HOOFD_030_2") + Node("01_HOOFD_040")
        Node("02_DRZ_010") joins Node("04_BPT_005")
        Node("04_BPT_005") splits Node("01_HOOFD_061")
        Node("01_HOOFD_015") + Node("01_HOOFD_020") + Node("03_GBH_005") or Node("01_HOOFD_015") + Node("01_HOOFD_020") + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_100") or Node("01_HOOFD_015") + Node("01_HOOFD_020") or Node("01_HOOFD_180") + Node("01_HOOFD_015") + Node(
            "01_HOOFD_540"
        ) + Node("01_HOOFD_020") + Node("01_HOOFD_100") or Node("01_HOOFD_180") + Node("01_HOOFD_015") + Node("01_HOOFD_510_2") + Node(
            "01_HOOFD_020"
        ) + Node("03_GBH_005") or Node("01_HOOFD_015") + Node("01_HOOFD_540") + Node("01_HOOFD_020") or Node("01_HOOFD_015") or Node(
            "01_HOOFD_020"
        ) + Node("03_GBH_005") or Node("01_HOOFD_015") + Node("01_HOOFD_540") + Node("01_HOOFD_020") + Node("01_HOOFD_100") join Node(
            "05_EIND_010"
        )
        Node("05_EIND_010") splits Node("01_HOOFD_260") or Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_040") or Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("01_HOOFD_015") + Node("06_VD_010") + Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_260") + Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node("06_VD_010") + Node("01_HOOFD_180") + Node(
            "01_HOOFD_260"
        ) or Node("01_HOOFD_065_2") + Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("06_VD_010") + Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_030_2"
        ) + Node("01_HOOFD_260") + Node("01_HOOFD_020") + Node("03_GBH_005") + Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node(
            "01_HOOFD_065_2"
        ) + Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_260") + Node(
            "01_HOOFD_020"
        ) + Node("03_GBH_005") + Node("01_HOOFD_040") or Node("01_HOOFD_015") + Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") or Node(
            "06_VD_010"
        ) + Node("01_HOOFD_260") or Node("01_HOOFD_180") + Node("06_VD_010") + Node("01_HOOFD_260") + Node("01_HOOFD_050") or Node(
            "01_HOOFD_065_2"
        ) + Node("06_VD_010") + Node("01_HOOFD_260") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("06_VD_010") + Node(
            "01_HOOFD_260"
        ) + Node("01_HOOFD_050") or Node("01_HOOFD_065_2") + Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("06_VD_010") + Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_260") + Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node("01_HOOFD_190") + Node(
            "01_HOOFD_260"
        ) or Node("01_HOOFD_190") + Node("01_HOOFD_180") + Node("06_VD_010") + Node("01_HOOFD_260") or Node("01_HOOFD_065_2") + Node(
            "01_HOOFD_190"
        ) + Node("01_HOOFD_180") + Node("06_VD_010") + Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2") + Node("01_HOOFD_260") + Node(
            "03_GBH_005"
        ) + Node("01_HOOFD_050") + Node("01_HOOFD_040") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("01_HOOFD_015") + Node(
            "01_HOOFD_030_1"
        ) + Node("01_HOOFD_030_2") or Node("01_HOOFD_065_2") + Node("01_HOOFD_180") + Node("01_HOOFD_030_1")
        Node("07_OPS_010") + Node("05_EIND_010") + Node("01_HOOFD_110") + Node("01_HOOFD_101") or Node("07_OPS_010") + Node(
            "05_EIND_010"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_100") or Node("07_OPS_010") + Node("05_EIND_010") + Node("01_HOOFD_110") or Node(
            "07_OPS_010"
        ) + Node("01_HOOFD_110") + Node("01_HOOFD_101") or Node("05_EIND_010") + Node("01_HOOFD_110") + Node("01_HOOFD_100") or Node(
            "07_OPS_010"
        ) join Node("06_VD_010")
        Node("06_VD_010") splits Node("01_HOOFD_190") + Node("01_HOOFD_200") + Node("01_HOOFD_180") + Node("01_HOOFD_380") + Node(
            "01_HOOFD_120"
        ) or Node("01_HOOFD_380") or Node("01_HOOFD_200") + Node("01_HOOFD_380") or Node("01_HOOFD_190") + Node("01_HOOFD_200") + Node(
            "01_HOOFD_180"
        ) + Node("01_HOOFD_380") or Node("01_HOOFD_190") + Node("01_HOOFD_200") + Node("01_HOOFD_180") + Node("01_HOOFD_380") + Node(
            "01_HOOFD_120"
        ) + Node("01_HOOFD_101") or Node("01_HOOFD_200") + Node("01_HOOFD_190") + Node("01_HOOFD_380") + Node("01_HOOFD_101") or Node(
            "01_HOOFD_200"
        ) + Node("01_HOOFD_190") + Node("01_HOOFD_380")
        Node("01_HOOFD_065_1") + Node("01_HOOFD_060") or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node("01_HOOFD_180") + Node(
            "01_HOOFD_110"
        ) or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node("01_HOOFD_110") or Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node(
            "01_HOOFD_180"
        ) + Node("01_HOOFD_370") + Node("01_HOOFD_110") or Node("01_HOOFD_060") join Node("07_OPS_010")
        Node("07_OPS_010") splits Node("06_VD_010") + Node("01_HOOFD_100") or Node("01_HOOFD_101") or Node("06_VD_010") + Node(
            "01_HOOFD_101"
        ) or Node("06_VD_010")
        Node("01_HOOFD_180") + Node("01_HOOFD_110_2") or Node("01_HOOFD_180") + Node("01_HOOFD_110_2") + Node("01_HOOFD_099") join Node(
            "08_AWB45_005"
        )
        Node("08_AWB45_005") splits Node("01_HOOFD_196")
        Node("01_HOOFD_190") joins Node("08_AWB45_010")
        Node("08_AWB45_010") splits Node("08_AWB45_020_2")
        Node("08_AWB45_020_2") joins Node("08_AWB45_020_1")
        Node("08_AWB45_020_1") splits Node("08_AWB45_040")
        Node("08_AWB45_010") joins Node("08_AWB45_020_2")
        Node("08_AWB45_020_2") splits Node("08_AWB45_020_1")
        Node("08_AWB45_040") joins Node("08_AWB45_030")
        Node("08_AWB45_030") splits Node("08_AWB45_170")
        Node("08_AWB45_020_1") joins Node("08_AWB45_040")
        Node("08_AWB45_040") splits Node("08_AWB45_030")
        Node("08_AWB45_030") joins Node("08_AWB45_170")
        Node("08_AWB45_170") splits Node("01_HOOFD_110")
        Node("01_HOOFD_330") + Node("15_NGV_010") or Node("01_HOOFD_330") or Node("01_HOOFD_330") + Node("01_HOOFD_350_2") + Node(
            "01_HOOFD_350_1"
        ) or Node("01_HOOFD_330") + Node("01_HOOFD_350_2") + Node("01_HOOFD_350_1") + Node("15_NGV_010") join Node("09_AH_I_010")
        Node("09_AH_I_010") splits Node("01_HOOFD_380") + Node("01_HOOFD_370") + Node("01_HOOFD_375")
        Node("01_HOOFD_465") joins Node("10_UOV_010")
        Node("10_UOV_010") splits Node("10_UOV_045")
        Node("10_UOV_010") joins Node("10_UOV_045")
        Node("10_UOV_045") splits Node("10_UOV_050_1")
        Node("10_UOV_045") joins Node("10_UOV_050_1")
        Node("10_UOV_050_1") splits Node("10_UOV_050_2")
        Node("10_UOV_050_1") joins Node("10_UOV_050_2")
        Node("10_UOV_050_2") splits Node("10_UOV_060")
        Node("10_UOV_050_2") joins Node("10_UOV_060")
        Node("10_UOV_060") splits Node("10_UOV_180")
        Node("10_UOV_060") joins Node("10_UOV_180")
        Node("10_UOV_180") splits Node("01_HOOFD_470")
        Node("01_HOOFD_430") joins Node("11_AH_II_010")
        Node("11_AH_II_010") splits Node("01_HOOFD_480")
        Node("01_HOOFD_030_2") + Node("01_HOOFD_020") or Node("01_HOOFD_020") + Node("03_GBH_005") or Node("01_HOOFD_020") or Node(
            "01_HOOFD_065_1"
        ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_020") or Node("03_GBH_005") join Node("14_VRIJ_010")
        Node("14_VRIJ_010") splits Node("01_HOOFD_030_1") + Node("03_GBH_005") or Node("01_HOOFD_030_1") or Node("01_HOOFD_030_1") + Node(
            "01_HOOFD_020"
        ) or Node("03_GBH_005")
        Node("01_HOOFD_200") + Node("01_HOOFD_260") + Node("01_HOOFD_250") or Node("01_HOOFD_200") + Node("01_HOOFD_330") + Node(
            "01_HOOFD_260"
        ) + Node("01_HOOFD_250") or Node("01_HOOFD_200") + Node("01_HOOFD_250") or Node("01_HOOFD_200") join Node("15_NGV_010")
        Node("15_NGV_010") splits Node("01_HOOFD_330") + Node("01_HOOFD_260") + Node("09_AH_I_010") or Node("09_AH_I_010") or Node(
            "01_HOOFD_330"
        ) + Node("09_AH_I_010")
        Node("01_HOOFD_060") + Node("01_HOOFD_530") + Node("01_HOOFD_810") + Node("01_HOOFD_195") or Node("01_HOOFD_810") + Node(
            "01_HOOFD_820"
        ) + Node("01_HOOFD_061") + Node("01_HOOFD_195") or Node("01_HOOFD_061") + Node("01_HOOFD_195") or Node("01_HOOFD_061") or Node(
            "01_HOOFD_060"
        ) or Node("01_HOOFD_060") + Node("01_HOOFD_530") + Node("01_HOOFD_800") + Node("01_HOOFD_195") or Node("01_HOOFD_060") + Node(
            "01_HOOFD_530"
        ) + Node("01_HOOFD_800") + Node("01_HOOFD_195") + Node("01_HOOFD_101") or Node("01_HOOFD_060") + Node("01_HOOFD_101") or Node(
            "01_HOOFD_060"
        ) + Node("01_HOOFD_530") + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node("01_HOOFD_195") or Node("01_HOOFD_060") + Node(
            "01_HOOFD_530"
        ) + Node("01_HOOFD_810") + Node("01_HOOFD_195") + Node("01_HOOFD_101") or Node("01_HOOFD_060") + Node("01_HOOFD_530") + Node(
            "01_HOOFD_810"
        ) + Node("01_HOOFD_820") + Node("01_HOOFD_195") + Node("01_HOOFD_101") or Node("01_HOOFD_060") + Node("01_HOOFD_195") + Node(
            "01_HOOFD_101"
        ) join end
        start splits Node("01_HOOFD_065_2") + Node("01_HOOFD_030_2") + Node("01_HOOFD_010") or Node("01_HOOFD_030_2") + Node(
            "01_HOOFD_010"
        )


    }
}