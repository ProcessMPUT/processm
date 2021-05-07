package processm.conformance.models.alignments.hmtests

import org.junit.jupiter.api.AfterAll
import processm.conformance.models.alignments.CompositeAligner
import processm.core.log.XMLXESInputStream
import processm.core.log.hierarchical.HoneyBadgerHierarchicalXESInputStream
import processm.core.log.hierarchical.InMemoryXESProcessing
import processm.core.log.hierarchical.Log
import processm.core.models.causalnet.Node
import processm.core.models.causalnet.causalnet
import processm.core.models.petrinet.converters.toPetriNet
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals

@InMemoryXESProcessing
class `BPIC15_2f complete` {

    // This model was created using WindowingHeuristicMiner given whole BPIC15_2f.xes.gz
    private val model =
        causalnet {
            start = Node("start", "", true)
            end = Node("end", "", true)
            (Node("01_HOOFD_510_2") + Node("01_HOOFD_510_2a") + Node("01_HOOFD_515")) or (Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_515"
            )) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_810") + Node("01_HOOFD_510_2a") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_515"
            )) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_530")) or (Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_810"
            )) join Node("01_BB_540")
            Node("01_BB_540") splits (Node("01_BB_775")) or (Node("01_BB_770")) or (Node("01_BB_775") + Node("01_BB_770"))
            (Node("01_HOOFD_820")) or (Node("01_BB_540")) or (Node("01_HOOFD_820") + Node("01_BB_540")) or (Node("01_HOOFD_510_2")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_820")) or (Node("01_HOOFD_510_2") + Node("01_BB_540")) or (Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_820"
            ) + Node("01_BB_540")) join Node("01_BB_770")
            Node("01_BB_770") splits (Node("END")) or (Node("01_BB_775") + Node("END")) or (Node("01_BB_775") + Node("01_HOOFD_810") + Node(
                "END"
            )) or (Node("01_HOOFD_810") + Node("END"))
            (Node("01_BB_540")) or (Node("01_BB_770") + Node("01_BB_540")) or (Node("01_BB_770")) join Node("01_BB_775")
            Node("01_BB_775") splits (Node("01_HOOFD_101b") + Node("01_HOOFD_811")) or (Node("01_HOOFD_811") + Node("01_HOOFD_810")) or (Node(
                "01_HOOFD_810"
            )) or (Node("01_HOOFD_811")) or (Node("01_HOOFD_101b") + Node("01_HOOFD_811") + Node("01_HOOFD_810"))
            (Node("START")) joins Node("01_HOOFD_010")
            Node("01_HOOFD_010") splits (Node("01_HOOFD_015")) or (Node("01_HOOFD_015") + Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_015"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_011")) or (Node("01_HOOFD_030_2")) or (Node("01_HOOFD_015") + Node(
                "01_HOOFD_011"
            ))
            (Node("01_HOOFD_010")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_010")) join Node("01_HOOFD_011")
            Node("01_HOOFD_011") splits (Node("01_HOOFD_015"))
            (Node("01_HOOFD_010")) or (Node("01_HOOFD_011") + Node("01_HOOFD_010")) join Node("01_HOOFD_015")
            Node("01_HOOFD_015") splits (Node("01_HOOFD_020"))
            (Node("01_HOOFD_015")) joins Node("01_HOOFD_020")
            Node("01_HOOFD_020") splits (Node("01_HOOFD_030_1") + Node("03_GBH_005")) or (Node("01_HOOFD_030_1")) or (Node(
                "03_GBH_005"
            ))
            (Node("01_HOOFD_020") + Node("05_EIND_010")) or (Node("01_HOOFD_020") + Node("16_LGSV_010") + Node("05_EIND_010")) or (Node(
                "01_HOOFD_020"
            )) or (Node("01_HOOFD_020") + Node("16_LGSV_010")) join Node("01_HOOFD_030_1")
            Node("01_HOOFD_030_1") splits (Node("02_DRZ_010") + Node("01_HOOFD_030_2") + Node("01_HOOFD_061")) or (Node(
                "01_HOOFD_030_2"
            ) + Node("01_HOOFD_040")) or (Node("01_HOOFD_040")) or (Node("02_DRZ_010") + Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_061"
            )) or (Node("02_DRZ_010")) or (Node("01_HOOFD_030_2")) or (Node("02_DRZ_010") + Node("01_HOOFD_061"))
            (Node("16_LGSV_010") + Node("01_HOOFD_010")) or (Node("16_LGSV_010") + Node("04_BPT_005") + Node("01_HOOFD_010")) or (Node(
                "01_HOOFD_030_1"
            ) + Node("16_LGSV_010") + Node("01_HOOFD_010")) or (Node("01_HOOFD_030_1") + Node("16_LGSV_010") + Node("04_BPT_005") + Node(
                "01_HOOFD_010"
            )) or (Node("01_HOOFD_010")) or (Node("01_HOOFD_030_1") + Node("01_HOOFD_010")) or (Node("01_HOOFD_030_1") + Node(
                "04_BPT_005"
            ) + Node("01_HOOFD_010")) or (Node("01_HOOFD_030_1")) join Node("01_HOOFD_030_2")
            Node("01_HOOFD_030_2") splits (Node("END")) or (Node("01_HOOFD_065_2")) or (Node("01_HOOFD_065_2") + Node("END")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("02_DRZ_010") + Node("END")) or (Node("01_HOOFD_110_0") + Node("END")) or (Node("01_HOOFD_065_2") + Node(
                "02_DRZ_010"
            ) + Node("01_HOOFD_110_0") + Node("END")) or (Node("02_DRZ_010") + Node("END")) or (Node("02_DRZ_010") + Node(
                "01_HOOFD_110_0"
            ) + Node("END")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_040") + Node("END"))
            (Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2")) or (Node("01_HOOFD_030_1")) or (Node("01_HOOFD_030_2")) join Node(
                "01_HOOFD_040"
            )
            Node("01_HOOFD_040") splits (Node("01_HOOFD_060") + Node("01_HOOFD_050")) or (Node("01_HOOFD_060"))
            (Node("01_HOOFD_065_2") + Node("01_HOOFD_060") + Node("01_HOOFD_040")) or (Node("04_BPT_005")) or (Node("01_HOOFD_060") + Node(
                "01_HOOFD_040"
            )) or (Node("01_HOOFD_065_2") + Node("04_BPT_005")) or (Node("01_HOOFD_040")) join Node("01_HOOFD_050")
            Node("01_HOOFD_050") splits (Node("01_HOOFD_065_1") + Node("01_HOOFD_060") + Node("01_HOOFD_130") + Node("01_HOOFD_110")) or (Node(
                "01_HOOFD_065_1"
            ) + Node("01_HOOFD_060") + Node("01_HOOFD_110")) or (Node("01_HOOFD_130") + Node("01_HOOFD_110")) or (Node("01_HOOFD_110")) or (Node(
                "01_HOOFD_065_1"
            )) or (Node("01_HOOFD_065_1") + Node("01_HOOFD_110"))
            (Node("01_HOOFD_050") + Node("01_HOOFD_040")) or (Node("01_HOOFD_040")) join Node("01_HOOFD_060")
            Node("01_HOOFD_060") splits (Node("01_HOOFD_065_1") + Node("01_HOOFD_050")) or (Node("01_HOOFD_050")) or (Node(
                "01_HOOFD_065_1"
            ) + Node("04_BPT_010")) or (Node("01_HOOFD_065_1"))
            (Node("02_DRZ_010") + Node("01_HOOFD_030_1")) or (Node("02_DRZ_010")) or (Node("01_HOOFD_030_1")) join Node(
                "01_HOOFD_061"
            )
            Node("01_HOOFD_061") splits (Node("01_HOOFD_065_0")) or (Node("01_HOOFD_065_0") + Node("04_BPT_005")) or (Node(
                "04_BPT_005"
            ))
            (Node("01_HOOFD_061")) or (Node("04_BPT_030")) join Node("01_HOOFD_065_0")
            Node("01_HOOFD_065_0") splits (Node("01_HOOFD_110_0")) or (Node("02_DRZ_010") + Node("01_HOOFD_090") + Node(
                "01_HOOFD_110_0"
            )) or (Node("02_DRZ_010")) or (Node("01_HOOFD_090")) or (Node("02_DRZ_010") + Node("01_HOOFD_110_0")) or (Node(
                "01_HOOFD_090"
            ) + Node("01_HOOFD_110_0")) or (Node("02_DRZ_010") + Node("01_HOOFD_090"))
            (Node("01_HOOFD_060") + Node("01_HOOFD_050")) or (Node("01_HOOFD_060") + Node("04_BPT_030") + Node("01_HOOFD_050")) or (Node(
                "01_HOOFD_060"
            ) + Node("04_BPT_030")) or (Node("01_HOOFD_050") + Node("04_BPT_005")) or (Node("04_BPT_005")) or (Node("04_BPT_030") + Node(
                "04_BPT_005"
            )) or (Node("01_HOOFD_060")) join Node("01_HOOFD_065_1")
            Node("01_HOOFD_065_1") splits (Node("01_HOOFD_065_2") + Node("01_HOOFD_110_1")) or (Node("01_HOOFD_110_1")) or (Node(
                "01_HOOFD_090"
            ) + Node("01_HOOFD_110")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_090") + Node("01_HOOFD_110")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("01_HOOFD_110")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_090")) or (Node("01_HOOFD_110")) or (Node(
                "01_HOOFD_065_2"
            )) or (Node("01_HOOFD_090") + Node("01_HOOFD_110_1")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_090") + Node(
                "01_HOOFD_110_1"
            ))
            (Node("01_HOOFD_030_2")) or (Node("01_HOOFD_065_1") + Node("01_HOOFD_030_2")) or (Node("01_HOOFD_065_1")) join Node(
                "01_HOOFD_065_2"
            )
            Node("01_HOOFD_065_2") splits (Node("01_HOOFD_110")) or (Node("01_HOOFD_090")) or (Node("01_HOOFD_110_1") + Node(
                "01_HOOFD_011"
            )) or (Node("01_HOOFD_090") + Node("01_HOOFD_110")) or (Node("06_VD_010") + Node("01_HOOFD_110")) or (Node("01_HOOFD_110") + Node(
                "01_HOOFD_050"
            )) or (Node("06_VD_010")) or (Node("01_HOOFD_011")) or (Node("01_HOOFD_090") + Node("01_HOOFD_110_1") + Node(
                "01_HOOFD_011"
            )) or (Node("01_HOOFD_090") + Node("01_HOOFD_011")) or (Node("06_VD_010") + Node("01_HOOFD_110") + Node("01_HOOFD_050")) or (Node(
                "01_HOOFD_110_1"
            )) or (Node("06_VD_010") + Node("01_HOOFD_110_1")) or (Node("01_HOOFD_090") + Node("01_HOOFD_110") + Node("01_HOOFD_011")) or (Node(
                "06_VD_010"
            ) + Node("01_HOOFD_090") + Node("01_HOOFD_110")) or (Node("01_HOOFD_090") + Node("01_HOOFD_110_1"))
            (Node("01_HOOFD_065_2")) or (Node("01_HOOFD_065_1")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_0")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("01_HOOFD_065_1")) or (Node("01_HOOFD_065_0")) join Node("01_HOOFD_090")
            Node("01_HOOFD_090") splits (Node("02_DRZ_010") + Node("01_HOOFD_110_0")) or (Node("02_DRZ_010")) or (Node("01_HOOFD_110_0")) or (Node(
                "01_HOOFD_130"
            ))
            (Node("01_HOOFD_100")) or (Node("01_HOOFD_120")) join Node("01_HOOFD_100")
            Node("01_HOOFD_100") splits (Node("01_HOOFD_100")) or (Node("06_VD_010"))
            (Node("01_BB_775")) joins Node("01_HOOFD_101b")
            Node("01_HOOFD_101b") splits (Node("01_HOOFD_809"))
            (Node("01_HOOFD_065_1") + Node("01_HOOFD_050")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_130") + Node(
                "01_HOOFD_050"
            )) or (Node("01_HOOFD_065_2")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_050")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("01_HOOFD_130")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1")) or (Node("01_HOOFD_065_1") + Node(
                "01_HOOFD_130"
            )) or (Node("01_HOOFD_130") + Node("01_HOOFD_050")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node(
                "06_VD_010"
            ) + Node("01_HOOFD_050")) or (Node("06_VD_010")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node(
                "06_VD_010"
            )) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_130")) or (Node("01_HOOFD_065_1")) join Node(
                "01_HOOFD_110"
            )
            Node("01_HOOFD_110") splits (Node("01_HOOFD_120"))
            (Node("01_HOOFD_065_0")) or (Node("01_HOOFD_065_0") + Node("01_HOOFD_090")) or (Node("01_HOOFD_065_0") + Node(
                "01_HOOFD_030_2"
            )) or (Node("01_HOOFD_065_0") + Node("01_HOOFD_090") + Node("01_HOOFD_030_2")) or (Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_090"
            ) + Node("01_HOOFD_030_2")) join Node("01_HOOFD_110_0")
            Node("01_HOOFD_110_0") splits (Node("01_HOOFD_180") + Node("01_HOOFD_110_1")) or (Node("01_HOOFD_180"))
            (Node("01_HOOFD_065_2") + Node("01_HOOFD_065_1") + Node("01_HOOFD_110_0")) or (Node("01_HOOFD_065_1")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("01_HOOFD_065_1") + Node("01_HOOFD_130")) or (Node("01_HOOFD_065_1") + Node("01_HOOFD_130")) or (Node(
                "01_HOOFD_065_2"
            ) + Node("01_HOOFD_065_1")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_130")) or (Node("01_HOOFD_130")) or (Node(
                "01_HOOFD_110_0"
            )) join Node("01_HOOFD_110_1")
            Node("01_HOOFD_110_1") splits (Node("01_HOOFD_110_2"))
            (Node("01_HOOFD_110_1")) joins Node("01_HOOFD_110_2")
            Node("01_HOOFD_110_2") splits (Node("01_HOOFD_180"))
            (Node("01_HOOFD_110")) joins Node("01_HOOFD_120")
            Node("01_HOOFD_120") splits (Node("01_HOOFD_100")) or (Node("01_HOOFD_180"))
            (Node("01_HOOFD_090") + Node("01_HOOFD_050")) or (Node("01_HOOFD_090")) or (Node("01_HOOFD_050")) join Node(
                "01_HOOFD_130"
            )
            Node("01_HOOFD_130") splits (Node("01_HOOFD_110")) or (Node("01_HOOFD_110_1"))
            (Node("01_HOOFD_110_0")) or (Node("01_HOOFD_110_2") + Node("01_HOOFD_110_0")) or (Node("01_HOOFD_120")) or (Node(
                "01_HOOFD_110_2"
            )) join Node("01_HOOFD_180")
            Node("01_HOOFD_180") splits (Node("06_VD_010") + Node("01_HOOFD_195")) or (Node("08_AWB45_005")) or (Node("06_VD_010")) or (Node(
                "01_HOOFD_195"
            )) or (Node("06_VD_010") + Node("08_AWB45_005") + Node("01_HOOFD_195")) or (Node("08_AWB45_005") + Node("01_HOOFD_195"))
            (Node("01_HOOFD_180") + Node("08_AWB45_005")) or (Node("01_HOOFD_180")) or (Node("01_HOOFD_180") + Node("06_VD_010")) or (Node(
                "06_VD_010"
            )) join Node("01_HOOFD_195")
            Node("01_HOOFD_195") splits (Node("01_HOOFD_200")) or (Node("01_HOOFD_200") + Node("08_AWB45_005")) or (Node(
                "08_AWB45_005"
            )) or (Node("01_HOOFD_200") + Node("01_HOOFD_196")) or (Node("01_HOOFD_200") + Node("08_AWB45_005") + Node("01_HOOFD_196"))
            (Node("08_AWB45_005") + Node("01_HOOFD_195")) or (Node("01_HOOFD_195")) join Node("01_HOOFD_196")
            Node("01_HOOFD_196") splits (Node("01_HOOFD_200"))
            (Node("01_HOOFD_195")) or (Node("01_HOOFD_195") + Node("01_HOOFD_196")) join Node("01_HOOFD_200")
            Node("01_HOOFD_200") splits (Node("08_AWB45_005")) or (Node("01_HOOFD_250")) or (Node("01_HOOFD_250_1")) or (Node(
                "01_HOOFD_270"
            ) + Node("01_HOOFD_250")) or (Node("08_AWB45_005") + Node("01_HOOFD_250_1"))
            (Node("01_HOOFD_200")) or (Node("01_HOOFD_200") + Node("01_HOOFD_270")) join Node("01_HOOFD_250")
            Node("01_HOOFD_250") splits (Node("01_HOOFD_260"))
            (Node("01_HOOFD_200")) or (Node("08_AWB45_005")) or (Node("01_HOOFD_200") + Node("08_AWB45_005")) join Node(
                "01_HOOFD_250_1"
            )
            Node("01_HOOFD_250_1") splits (Node("01_HOOFD_250_2"))
            (Node("01_HOOFD_250_1")) joins Node("01_HOOFD_250_2")
            Node("01_HOOFD_250_2") splits (Node("01_HOOFD_330"))
            (Node("01_HOOFD_250")) joins Node("01_HOOFD_260")
            Node("01_HOOFD_260") splits (Node("01_HOOFD_330"))
            (Node("01_HOOFD_200")) joins Node("01_HOOFD_270")
            Node("01_HOOFD_270") splits (Node("01_HOOFD_250"))
            (Node("01_HOOFD_260")) or (Node("01_HOOFD_250_2")) join Node("01_HOOFD_330")
            Node("01_HOOFD_330") splits (Node("09_AH_I_010")) or (Node("09_AH_I_010") + Node("01_HOOFD_375")) or (Node("01_HOOFD_375")) or (Node(
                "01_HOOFD_370"
            ) + Node("01_HOOFD_375")) or (Node("01_HOOFD_370") + Node("09_AH_I_010") + Node("01_HOOFD_375"))
            (Node("01_HOOFD_330") + Node("09_AH_I_010")) or (Node("01_HOOFD_330")) or (Node("09_AH_I_010")) join Node("01_HOOFD_370")
            Node("01_HOOFD_370") splits (Node("01_HOOFD_375"))
            (Node("01_HOOFD_370")) or (Node("01_HOOFD_330") + Node("01_HOOFD_370")) or (Node("01_HOOFD_330")) join Node(
                "01_HOOFD_375"
            )
            Node("01_HOOFD_375") splits (Node("01_HOOFD_380"))
            (Node("01_HOOFD_375")) joins Node("01_HOOFD_380")
            Node("01_HOOFD_380") splits (Node("09_AH_I_010")) or (Node("01_HOOFD_430")) or (Node("01_HOOFD_430") + Node(
                "09_AH_I_010"
            ))
            (Node("01_HOOFD_380")) joins Node("01_HOOFD_430")
            Node("01_HOOFD_430") splits (Node("01_HOOFD_480")) or (Node("11_AH_II_010") + Node("09_AH_I_010")) or (Node(
                "11_AH_II_010"
            )) or (Node("01_HOOFD_480") + Node("11_AH_II_010")) or (Node("01_HOOFD_480") + Node("11_AH_II_010") + Node("09_AH_I_010")) or (Node(
                "09_AH_I_010"
            ))
            (Node("01_HOOFD_430")) or (Node("01_HOOFD_430") + Node("11_AH_II_010")) join Node("01_HOOFD_480")
            Node("01_HOOFD_480") splits (Node("01_HOOFD_490_1"))
            (Node("13_CRD_010") + Node("09_AH_I_010")) or (Node("09_AH_I_010")) or (Node("13_CRD_010")) or (Node("01_HOOFD_480")) or (Node(
                "01_HOOFD_480"
            ) + Node("09_AH_I_010")) join Node("01_HOOFD_490_1")
            Node("01_HOOFD_490_1") splits (Node("01_HOOFD_490_1a") + Node("01_HOOFD_490_2")) or (Node("09_AH_I_010") + Node(
                "01_HOOFD_490_2"
            )) or (Node("01_HOOFD_490_2")) or (Node("09_AH_I_010")) or (Node("01_HOOFD_490_1a") + Node("09_AH_I_010") + Node(
                "01_HOOFD_490_2"
            ))
            (Node("01_HOOFD_490_1")) joins Node("01_HOOFD_490_1a")
            Node("01_HOOFD_490_1a") splits (Node("01_HOOFD_490_2"))
            (Node("01_HOOFD_490_1a") + Node("11_AH_II_010") + Node("01_HOOFD_490_1")) or (Node("11_AH_II_010")) or (Node(
                "11_AH_II_010"
            ) + Node("01_HOOFD_490_1")) or (Node("01_HOOFD_490_1a") + Node("01_HOOFD_490_1")) or (Node("01_HOOFD_490_1")) join Node(
                "01_HOOFD_490_2"
            )
            Node("01_HOOFD_490_2") splits (Node("08_AWB45_005") + Node("01_HOOFD_491") + Node("01_HOOFD_490_4")) or (Node(
                "01_HOOFD_491"
            )) or (Node("08_AWB45_005")) or (Node("01_HOOFD_491") + Node("01_HOOFD_490_4")) or (Node("01_HOOFD_491") + Node(
                "08_AWB45_005"
            )) or (Node("01_HOOFD_490_4")) or (Node("01_HOOFD_490_3"))
            (Node("01_HOOFD_490_2")) joins Node("01_HOOFD_490_3")
            Node("01_HOOFD_490_3") splits (Node("01_HOOFD_495") + Node("01_HOOFD_510_1")) or (Node("01_HOOFD_510_1"))
            (Node("13_CRD_010")) or (Node("13_CRD_010") + Node("01_HOOFD_490_2")) or (Node("01_HOOFD_490_2")) join Node(
                "01_HOOFD_490_4"
            )
            Node("01_HOOFD_490_4") splits (Node("01_HOOFD_491")) or (Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_491") + Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_491") + Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_491") + Node("01_HOOFD_490_5a") + Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_490_5"))
            (Node("01_HOOFD_491") + Node("01_HOOFD_490_4") + Node("01_HOOFD_515") + Node("16_LGSD_010")) or (Node("01_HOOFD_494a") + Node(
                "01_HOOFD_491"
            ) + Node("13_CRD_010") + Node("01_HOOFD_490_4") + Node("01_HOOFD_515")) or (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node(
                "01_HOOFD_490_4"
            )) or (Node("13_CRD_010")) or (Node("13_CRD_010") + Node("16_LGSD_010")) or (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node(
                "01_HOOFD_490_4"
            ) + Node("16_LGSD_010")) or (Node("01_HOOFD_491") + Node("01_HOOFD_490_4")) or (Node("13_CRD_010") + Node("01_HOOFD_490_4")) or (Node(
                "01_HOOFD_494a"
            ) + Node("01_HOOFD_491") + Node("13_CRD_010") + Node("01_HOOFD_490_4") + Node("01_HOOFD_515") + Node("16_LGSD_010")) or (Node(
                "01_HOOFD_491"
            ) + Node("01_HOOFD_490_4") + Node("01_HOOFD_515")) or (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node("13_CRD_010") + Node(
                "01_HOOFD_490_4"
            )) or (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node("01_HOOFD_490_4") + Node("01_HOOFD_515") + Node("16_LGSD_010")) or (Node(
                "01_HOOFD_494a"
            ) + Node("01_HOOFD_491") + Node("01_HOOFD_490_4") + Node("01_HOOFD_515")) or (Node("01_HOOFD_490_4")) or (Node(
                "01_HOOFD_494a"
            ) + Node("01_HOOFD_491") + Node("13_CRD_010") + Node("01_HOOFD_490_4") + Node("16_LGSD_010")) join Node("01_HOOFD_490_5")
            Node("01_HOOFD_490_5") splits (Node("01_HOOFD_495") + Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_495") + Node(
                "01_HOOFD_491"
            )) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node("01_HOOFD_491") + Node("01_HOOFD_490_5a")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_510_2")) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_491"
            )) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_2")) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_490_5a"
            ))
            (Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_4")) join Node(
                "01_HOOFD_490_5a"
            )
            Node("01_HOOFD_490_5a") splits (Node("01_HOOFD_495") + Node("01_HOOFD_510_2") + Node("01_HOOFD_491")) or (Node(
                "01_HOOFD_495"
            ) + Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_491")) or (Node("01_HOOFD_510_2")) or (Node(
                "01_HOOFD_495"
            )) or (Node("01_HOOFD_495") + Node("01_HOOFD_491"))
            (Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_4")) or (Node("01_HOOFD_490_2") + Node(
                "01_HOOFD_490_4"
            )) or (Node("01_HOOFD_490_2")) or (Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_4")) or (Node("01_HOOFD_490_5a") + Node(
                "01_HOOFD_490_2"
            ) + Node("01_HOOFD_490_4")) or (Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_2") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_490_2"
            ) + Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5")) join Node("01_HOOFD_491")
            Node("01_HOOFD_491") splits (Node("01_HOOFD_495")) or (Node("01_HOOFD_495") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_495") + Node("01_HOOFD_494a")) or (Node("01_HOOFD_495") + Node("01_HOOFD_494a") + Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_494a")) or (Node("01_HOOFD_494a") + Node("01_HOOFD_490_5"))
            (Node("01_HOOFD_491")) joins Node("01_HOOFD_494a")
            Node("01_HOOFD_494a") splits (Node("08_AWB45_005") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_495") + Node(
                "08_AWB45_005"
            )) or (Node("01_HOOFD_495")) or (Node("08_AWB45_005")) or (Node("01_HOOFD_490_5")) or (Node("01_HOOFD_495") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_495") + Node("01_HOOFD_490_5"))
            (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_491")) or (Node(
                "01_HOOFD_491"
            ) + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_494a") + Node(
                "01_HOOFD_491"
            )) or (Node("01_HOOFD_494a") + Node("01_HOOFD_491") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_3")) or (Node(
                "01_HOOFD_494a"
            ) + Node("01_HOOFD_491") + Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_5")) join Node("01_HOOFD_495")
            Node("01_HOOFD_495") splits (Node("01_HOOFD_510_1")) or (Node("01_HOOFD_500")) or (Node("01_HOOFD_510_1") + Node(
                "01_HOOFD_500"
            ))
            (Node("01_HOOFD_495")) joins Node("01_HOOFD_500")
            Node("01_HOOFD_500") splits (Node("01_HOOFD_510_0") + Node("16_LGSD_010")) or (Node("01_HOOFD_510_1") + Node(
                "16_LGSD_010"
            )) or (Node("01_HOOFD_510_0") + Node("01_HOOFD_510_1")) or (Node("01_HOOFD_510_1")) or (Node("01_HOOFD_510_0") + Node(
                "01_HOOFD_510_1"
            ) + Node("16_LGSD_010"))
            (Node("01_HOOFD_500")) joins Node("01_HOOFD_510_0")
            Node("01_HOOFD_510_0") splits (Node("01_HOOFD_510_1") + Node("16_LGSD_010")) or (Node("01_HOOFD_510_1")) or (Node(
                "16_LGSD_010"
            ))
            (Node("01_HOOFD_495")) or (Node("01_HOOFD_490_3")) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_0") + Node(
                "01_HOOFD_500"
            )) or (Node("01_HOOFD_495") + Node("01_HOOFD_510_0") + Node("01_HOOFD_500") + Node("16_LGSD_010")) or (Node(
                "01_HOOFD_495"
            ) + Node("01_HOOFD_490_3")) or (Node("01_HOOFD_495") + Node("01_HOOFD_500")) or (Node("01_HOOFD_495") + Node(
                "01_HOOFD_500"
            ) + Node("16_LGSD_010")) or (Node("01_HOOFD_495") + Node("01_HOOFD_500") + Node("01_HOOFD_490_3")) join Node(
                "01_HOOFD_510_1"
            )
            Node("01_HOOFD_510_1") splits (Node("01_HOOFD_510_3") + Node("01_HOOFD_510_2a")) or (Node("01_HOOFD_510_2") + Node(
                "01_HOOFD_510_3"
            )) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_510_2a")) or (Node("01_HOOFD_510_3")) or (Node("01_HOOFD_510_2a")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_510_3") + Node("01_HOOFD_510_2a")) or (Node("01_HOOFD_510_2"))
            (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5a")) or (Node(
                "01_HOOFD_510_1"
            ) + Node("01_HOOFD_515")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_5") + Node(
                "01_HOOFD_515"
            )) or (Node("01_HOOFD_490_5")) or (Node("01_HOOFD_510_1")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5a") + Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_490_5a")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5a") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_510_1"
            ) + Node("01_HOOFD_490_5") + Node("01_HOOFD_515")) or (Node("01_HOOFD_490_5a") + Node("01_HOOFD_490_5")) join Node(
                "01_HOOFD_510_2"
            )
            Node("01_HOOFD_510_2") splits (Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_HOOFD_510_2a") + Node("END")) or (Node(
                "END"
            )) or (Node("01_HOOFD_810") + Node("END")) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_810") + Node("01_BB_540") + Node(
                "END"
            )) or (Node("01_HOOFD_510_3") + Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_510_2a") + Node("END")) or (Node(
                "01_HOOFD_510_3"
            ) + Node("01_BB_770") + Node("01_BB_540") + Node("END")) or (Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_510_2a") + Node(
                "END"
            )) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_510_2a") + Node(
                "END"
            )) or (Node("01_BB_770") + Node("END")) or (Node("01_HOOFD_810") + Node("01_BB_770") + Node("END")) or (Node(
                "01_HOOFD_510_3"
            ) + Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_HOOFD_510_2a") + Node("END")) or (Node("01_BB_540") + Node(
                "END"
            )) or (Node("01_HOOFD_510_3") + Node("01_BB_770") + Node("01_HOOFD_510_2a") + Node("END")) or (Node("01_HOOFD_810") + Node(
                "01_BB_770"
            ) + Node("01_BB_540") + Node("END")) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_510_2a") + Node("END")) or (Node(
                "01_BB_770"
            ) + Node("01_BB_540") + Node("END")) or (Node("01_HOOFD_510_3") + Node("END")) or (Node("01_HOOFD_810") + Node(
                "01_HOOFD_510_2a"
            ) + Node("END")) or (Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_510_2a") + Node(
                "END"
            )) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_BB_540") + Node("END")) or (Node(
                "01_HOOFD_510_3"
            ) + Node("01_BB_540") + Node("END")) or (Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_520") + Node(
                "END"
            )) or (Node("01_HOOFD_810") + Node("01_BB_770") + Node("01_BB_540") + Node("01_HOOFD_520") + Node("END")) or (Node(
                "01_HOOFD_510_3"
            ) + Node("01_HOOFD_810") + Node("01_BB_770") + Node("END"))
            (Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_1")) join Node(
                "01_HOOFD_510_2a"
            )
            Node("01_HOOFD_510_2a") splits (Node("01_HOOFD_510_3")) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_810") + Node(
                "01_BB_540"
            )) or (Node("01_HOOFD_810") + Node("01_BB_540")) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_810")) or (Node(
                "01_HOOFD_810"
            )) or (Node("01_HOOFD_510_3") + Node("01_BB_540")) or (Node("01_BB_540"))
            (Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2a")) or (Node(
                "01_HOOFD_510_1"
            )) or (Node("01_HOOFD_510_2")) or (Node("01_HOOFD_510_1") + Node("01_HOOFD_510_2") + Node("01_HOOFD_510_2a")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_510_2a")) join Node("01_HOOFD_510_3")
            Node("01_HOOFD_510_3") splits (Node("01_HOOFD_510_4") + Node("01_HOOFD_515")) or (Node("01_HOOFD_515"))
            (Node("01_HOOFD_510_3")) joins Node("01_HOOFD_510_4")
            Node("01_HOOFD_510_4") splits (Node("01_HOOFD_515"))
            (Node("01_HOOFD_510_3")) or (Node("01_HOOFD_510_3") + Node("01_HOOFD_510_4")) join Node("01_HOOFD_515")
            Node("01_HOOFD_515") splits (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_809")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_809") + Node("01_HOOFD_490_5") + Node(
                "16_LGSD_010"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_BB_540") + Node("01_HOOFD_809") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_BB_540") + Node("01_HOOFD_809")) or (Node("01_BB_540")) or (Node("01_HOOFD_510_2") + Node("01_BB_540") + Node(
                "01_HOOFD_490_5"
            )) or (Node("08_AWB45_005")) or (Node("01_HOOFD_510_2") + Node("01_BB_540") + Node("01_HOOFD_809") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_BB_540") + Node("01_HOOFD_809") + Node(
                "01_HOOFD_490_5"
            ) + Node("16_LGSD_010")) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_BB_540") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_510_2"
            )) or (Node("01_HOOFD_809")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_BB_540")) or (Node("01_HOOFD_510_2") + Node("01_BB_540") + Node("01_HOOFD_809") + Node("01_HOOFD_490_5") + Node(
                "16_LGSD_010"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_510_2") + Node(
                "01_BB_540"
            ) + Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node("01_BB_540") + Node("01_HOOFD_809"))
            (Node("01_HOOFD_510_2")) joins Node("01_HOOFD_520")
            Node("01_HOOFD_520") splits (Node("01_HOOFD_530"))
            (Node("01_HOOFD_520")) joins Node("01_HOOFD_530")
            Node("01_HOOFD_530") splits (Node("01_BB_540"))
            (Node("01_HOOFD_515")) or (Node("01_HOOFD_101b")) or (Node("01_HOOFD_101b") + Node("01_HOOFD_515")) join Node(
                "01_HOOFD_809"
            )
            Node("01_HOOFD_809") splits (Node("01_HOOFD_811"))
            (Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_510_2a")) or (Node(
                "01_BB_770"
            ) + Node("01_HOOFD_510_2a")) or (Node("01_HOOFD_510_2a")) or (Node("01_HOOFD_510_2") + Node("01_BB_770") + Node(
                "01_HOOFD_510_2a"
            )) or (Node("01_BB_775") + Node("01_HOOFD_510_2") + Node("01_BB_770")) or (Node("01_BB_775") + Node("01_BB_770")) or (Node(
                "01_BB_775"
            )) or (Node("01_BB_775") + Node("01_HOOFD_510_2") + Node("01_BB_770") + Node("01_HOOFD_510_2a")) or (Node("01_BB_775") + Node(
                "01_HOOFD_510_2"
            )) or (Node("01_HOOFD_510_2")) join Node("01_HOOFD_810")
            Node("01_HOOFD_810") splits (Node("END")) or (Node("01_HOOFD_820") + Node("01_BB_540") + Node("01_HOOFD_814") + Node(
                "END"
            )) or (Node("01_HOOFD_820") + Node("01_HOOFD_814") + Node("END")) or (Node("01_HOOFD_820") + Node("END"))
            (Node("01_BB_775") + Node("01_HOOFD_809")) or (Node("01_HOOFD_809")) or (Node("01_BB_775")) join Node("01_HOOFD_811")
            Node("01_HOOFD_811") splits (Node("01_HOOFD_814"))
            (Node("01_HOOFD_810")) or (Node("01_HOOFD_811") + Node("01_HOOFD_810")) or (Node("01_HOOFD_811")) join Node(
                "01_HOOFD_814"
            )
            Node("01_HOOFD_814") splits (Node("01_HOOFD_815"))
            (Node("01_HOOFD_814")) joins Node("01_HOOFD_815")
            Node("01_HOOFD_815") splits (Node("01_HOOFD_820"))
            (Node("01_HOOFD_810") + Node("01_HOOFD_815")) or (Node("01_HOOFD_815")) or (Node("01_HOOFD_810")) join Node(
                "01_HOOFD_820"
            )
            Node("01_HOOFD_820") splits (Node("END") + Node("16_LGSD_010")) or (Node("01_BB_770") + Node("END") + Node("16_LGSD_010")) or (Node(
                "01_BB_770"
            ) + Node("END")) or (Node("END"))
            (Node("01_HOOFD_030_2")) or (Node("01_HOOFD_030_1") + Node("01_HOOFD_030_2")) or (Node("01_HOOFD_090") + Node(
                "01_HOOFD_065_0"
            ) + Node("01_HOOFD_030_1")) or (Node("01_HOOFD_030_1")) or (Node("01_HOOFD_065_0") + Node("01_HOOFD_030_1")) join Node(
                "02_DRZ_010"
            )
            Node("02_DRZ_010") splits (Node("03_GBH_005")) or (Node("04_BPT_005")) or (Node("03_GBH_005") + Node("04_BPT_005")) or (Node(
                "01_HOOFD_061"
            )) or (Node("01_HOOFD_061") + Node("04_BPT_005"))
            (Node("02_DRZ_010") + Node("01_HOOFD_020")) or (Node("01_HOOFD_020")) join Node("03_GBH_005")
            Node("03_GBH_005") splits (Node("05_EIND_010") + Node("04_BPT_005")) or (Node("16_LGSV_010") + Node("04_BPT_005")) or (Node(
                "04_BPT_005"
            )) or (Node("16_LGSV_010") + Node("05_EIND_010")) or (Node("16_LGSV_010") + Node("05_EIND_010") + Node("04_BPT_005")) or (Node(
                "16_LGSV_010"
            )) or (Node("05_EIND_010"))
            (Node("02_DRZ_010")) or (Node("02_DRZ_010") + Node("03_GBH_005") + Node("01_HOOFD_061")) or (Node("03_GBH_005")) or (Node(
                "02_DRZ_010"
            ) + Node("03_GBH_005")) or (Node("02_DRZ_010") + Node("01_HOOFD_061")) join Node("04_BPT_005")
            Node("04_BPT_005") splits (Node("01_HOOFD_065_1") + Node("04_BPT_010")) or (Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_065_1"
            ) + Node("01_HOOFD_050")) or (Node("04_BPT_010")) or (Node("01_HOOFD_065_1")) or (Node("01_HOOFD_030_2") + Node(
                "05_EIND_010"
            ))
            (Node("04_BPT_005")) or (Node("01_HOOFD_060")) join Node("04_BPT_010")
            Node("04_BPT_010") splits (Node("04_BPT_020"))
            (Node("04_BPT_010")) joins Node("04_BPT_020")
            Node("04_BPT_020") splits (Node("04_BPT_030"))
            (Node("04_BPT_020")) joins Node("04_BPT_030")
            Node("04_BPT_030") splits (Node("01_HOOFD_065_0")) or (Node("01_HOOFD_065_1"))
            (Node("03_GBH_005") + Node("04_BPT_005")) or (Node("03_GBH_005")) join Node("05_EIND_010")
            Node("05_EIND_010") splits (Node("16_LGSV_010")) or (Node("01_HOOFD_030_1") + Node("16_LGSV_010")) or (Node(
                "01_HOOFD_030_1"
            ))
            (Node("01_HOOFD_180")) or (Node("01_HOOFD_065_2")) or (Node("01_HOOFD_065_2") + Node("01_HOOFD_180")) or (Node(
                "01_HOOFD_100"
            )) join Node("06_VD_010")
            Node("06_VD_010") splits (Node("01_HOOFD_110") + Node("01_HOOFD_195")) or (Node("01_HOOFD_195"))
            (Node("01_HOOFD_200") + Node("01_HOOFD_180") + Node("01_HOOFD_494a") + Node("01_HOOFD_490_2") + Node("01_HOOFD_195")) or (Node(
                "01_HOOFD_200"
            ) + Node("01_HOOFD_494a") + Node("01_HOOFD_490_2") + Node("01_HOOFD_195")) or (Node("01_HOOFD_180")) or (Node(
                "01_HOOFD_200"
            ) + Node("01_HOOFD_180") + Node("01_HOOFD_494a") + Node("01_HOOFD_490_2") + Node("01_HOOFD_195") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_195"
            )) or (Node("01_HOOFD_200") + Node("01_HOOFD_195")) or (Node("01_HOOFD_200") + Node("01_HOOFD_180") + Node("01_HOOFD_195")) or (Node(
                "01_HOOFD_200"
            ) + Node("01_HOOFD_180") + Node("01_HOOFD_490_2") + Node("01_HOOFD_195")) or (Node("01_HOOFD_200") + Node("01_HOOFD_490_2") + Node(
                "01_HOOFD_195"
            )) or (Node("01_HOOFD_200") + Node("01_HOOFD_494a") + Node("01_HOOFD_490_2") + Node("01_HOOFD_195") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_180"
            ) + Node("01_HOOFD_195")) join Node("08_AWB45_005")
            Node("08_AWB45_005") splits (Node("09_AH_I_010") + Node("01_HOOFD_250_1") + Node("END") + Node("01_HOOFD_196")) or (Node(
                "END"
            )) or (Node("09_AH_I_010") + Node("END")) or (Node("09_AH_I_010") + Node("01_HOOFD_250_1") + Node("END")) or (Node(
                "01_HOOFD_250_1"
            ) + Node("END")) or (Node("01_HOOFD_250_1") + Node("01_HOOFD_195") + Node("01_HOOFD_196")) or (Node("01_HOOFD_250_1") + Node(
                "END"
            ) + Node("01_HOOFD_195")) or (Node("01_HOOFD_195") + Node("01_HOOFD_196")) or (Node("01_HOOFD_250_1") + Node(
                "END"
            ) + Node("01_HOOFD_196")) or (Node("09_AH_I_010") + Node("01_HOOFD_250_1") + Node("END") + Node("01_HOOFD_195") + Node(
                "01_HOOFD_196"
            )) or (Node("01_HOOFD_195")) or (Node("01_HOOFD_250_1") + Node("END") + Node("01_HOOFD_195") + Node("01_HOOFD_196"))
            (Node("01_HOOFD_430") + Node("01_HOOFD_380")) or (Node("01_HOOFD_330")) or (Node("01_HOOFD_380")) or (Node("08_AWB45_005")) or (Node(
                "01_HOOFD_430"
            ) + Node("01_HOOFD_330") + Node("08_AWB45_005") + Node("01_HOOFD_380")) or (Node("01_HOOFD_330") + Node("08_AWB45_005")) or (Node(
                "01_HOOFD_330"
            ) + Node("08_AWB45_005") + Node("01_HOOFD_380")) or (Node("01_HOOFD_330") + Node("01_HOOFD_380")) or (Node("01_HOOFD_430") + Node(
                "01_HOOFD_330"
            ) + Node("01_HOOFD_380")) or (Node("01_HOOFD_430") + Node("01_HOOFD_330") + Node("01_HOOFD_380") + Node("01_HOOFD_490_1")) or (Node(
                "01_HOOFD_430"
            ) + Node("01_HOOFD_330") + Node("08_AWB45_005") + Node("01_HOOFD_380") + Node("01_HOOFD_490_1")) join Node("09_AH_I_010")
            Node("09_AH_I_010") splits (Node("01_HOOFD_490_1")) or (Node("11_AH_II_010")) or (Node("01_HOOFD_370") + Node(
                "01_HOOFD_490_1"
            )) or (Node("01_HOOFD_370") + Node("01_HOOFD_490_1") + Node("11_AH_II_010")) or (Node("01_HOOFD_370")) or (Node(
                "01_HOOFD_490_1"
            ) + Node("11_AH_II_010")) or (Node("01_HOOFD_370") + Node("11_AH_II_010"))
            (Node("01_HOOFD_430") + Node("09_AH_I_010")) or (Node("01_HOOFD_430")) or (Node("09_AH_I_010")) join Node("11_AH_II_010")
            Node("11_AH_II_010") splits (Node("13_CRD_010") + Node("01_HOOFD_490_2")) or (Node("13_CRD_010")) or (Node("01_HOOFD_490_2")) or (Node(
                "01_HOOFD_480"
            ) + Node("01_HOOFD_490_2"))
            (Node("11_AH_II_010")) joins Node("13_CRD_010")
            Node("13_CRD_010") splits (Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node("01_HOOFD_490_1") + Node(
                "01_HOOFD_490_4"
            ) + Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_490_1"
            ) + Node("01_HOOFD_490_4") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_1") + Node("01_HOOFD_490_4")) or (Node(
                "01_HOOFD_490_5"
            )) or (Node("01_HOOFD_490_5") + Node("16_LGSD_010")) or (Node("01_HOOFD_490_4"))
            (Node("13_CRD_010")) or (Node("01_HOOFD_510_0") + Node("01_HOOFD_500")) or (Node("01_HOOFD_510_0") + Node("01_HOOFD_820") + Node(
                "01_HOOFD_500"
            ) + Node("01_HOOFD_515")) or (Node("01_HOOFD_510_0") + Node("13_CRD_010") + Node("01_HOOFD_820") + Node("01_HOOFD_500") + Node(
                "01_HOOFD_515"
            )) or (Node("01_HOOFD_500")) or (Node("01_HOOFD_510_0") + Node("01_HOOFD_500") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_510_0"
            ) + Node("13_CRD_010") + Node("01_HOOFD_500") + Node("01_HOOFD_515")) or (Node("01_HOOFD_500") + Node("01_HOOFD_515")) or (Node(
                "01_HOOFD_510_0"
            ) + Node("13_CRD_010") + Node("01_HOOFD_500")) join Node("16_LGSD_010")
            Node("16_LGSD_010") splits (Node("01_HOOFD_510_1") + Node("01_HOOFD_490_5")) or (Node("01_HOOFD_490_5")) or (Node(
                "01_HOOFD_510_1"
            ))
            (Node("03_GBH_005")) or (Node("05_EIND_010") + Node("03_GBH_005")) join Node("16_LGSV_010")
            Node("16_LGSV_010") splits (Node("01_HOOFD_030_2")) or (Node("01_HOOFD_030_1")) or (Node("01_HOOFD_030_1") + Node(
                "01_HOOFD_030_2"
            ))
            (Node("01_HOOFD_510_2") + Node("01_HOOFD_810") + Node("01_BB_770")) or (Node("01_HOOFD_810")) or (Node("01_HOOFD_510_2") + Node(
                "01_BB_770"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_030_2") + Node("01_HOOFD_820")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("08_AWB45_005") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node(
                "01_BB_770"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_030_2")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node("01_HOOFD_820")) or (Node("08_AWB45_005") + Node("01_HOOFD_030_2") + Node(
                "01_HOOFD_810"
            ) + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("08_AWB45_005") + Node("01_HOOFD_030_2")) or (Node("08_AWB45_005")) or (Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_030_2") + Node("01_BB_770")) or (Node("08_AWB45_005") + Node("01_HOOFD_030_2") + Node("01_HOOFD_820")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node("01_BB_770")) or (Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("08_AWB45_005") + Node("01_HOOFD_030_2") + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_030_2") + Node(
                "01_HOOFD_810"
            )) or (Node("08_AWB45_005") + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_030_2") + Node("01_BB_770")) or (Node("01_HOOFD_030_2") + Node("01_HOOFD_820")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_820")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_820") + Node(
                "01_BB_770"
            )) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_030_2") + Node("01_HOOFD_810")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_820")) or (Node("01_HOOFD_030_2") + Node("01_HOOFD_810") + Node(
                "01_HOOFD_820"
            ) + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("08_AWB45_005") + Node("01_HOOFD_810") + Node("01_BB_770")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_030_2") + Node("01_BB_770")) or (Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node(
                "08_AWB45_005"
            ) + Node("01_HOOFD_810") + Node("01_HOOFD_820") + Node("01_BB_770")) or (Node("01_HOOFD_510_2") + Node("01_HOOFD_030_2")) or (Node(
                "01_HOOFD_510_2"
            ) + Node("01_HOOFD_030_2") + Node("01_HOOFD_820") + Node("01_BB_770")) join Node("END")
            Node("END") splits (end)
            (start) joins Node("START")
            Node("START") splits (Node("01_HOOFD_010"))
            (Node("END")) joins end
            start splits (Node("START"))
        }

    companion object {
        val pool = Executors.newCachedThreadPool()

        @JvmStatic
        @AfterAll
        fun cleanUp() {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun load(logfile: String): Log {
        File(logfile).inputStream().use { base ->
            return HoneyBadgerHierarchicalXESInputStream(XMLXESInputStream(GZIPInputStream(base))).first()
        }
    }

    @Test
    fun `trace 443`() {
        val net = model.toPetriNet()
        val aligner = CompositeAligner(net, pool = pool)
        val log = load("../xes-logs/BPIC15_2f.xes.gz")
        val trace = log.traces.toList()[443]
        val start = System.currentTimeMillis()
        val alignment = aligner.align(trace)
        val time = System.currentTimeMillis() - start

        println("Calculated alignment in ${time}ms: $alignment\tcost: ${alignment.cost}")

        assertEquals(0, alignment.cost)
    }
}
