package processm.core.esb

import io.hawt.embedded.HawtioMain
import io.hawt.embedded.Options
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger

class Hawtio : Service {
    private lateinit var hawtio: HawtioMain

    override val name = "Hawtio"

    override var status = ServiceStatus.Unknown
        private set

    override fun register() {
        logger().enter()

        hawtio = HawtioMain()
        status = ServiceStatus.Stopped

        logger().exit()
    }

    override fun start() {
        logger().enter()

        hawtio.war = javaClass.classLoader.getResource("hawtio-war-2.8.0.war").toString()
        hawtio.run(false)
        status = ServiceStatus.Started

        logger().exit()
    }

    override fun stop() {
        logger().enter()

        hawtio.stop()
        status = ServiceStatus.Stopped

        logger().exit()
    }


}