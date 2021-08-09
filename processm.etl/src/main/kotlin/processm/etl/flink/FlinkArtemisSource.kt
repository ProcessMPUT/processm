package processm.etl.flink

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.ResultTypeQueryable
import org.apache.flink.streaming.api.functions.source.SourceFunction
import processm.core.esb.ServiceStatus
import processm.core.logging.enter
import processm.core.logging.exit
import processm.core.logging.logger
import java.io.Serializable

class FlinkArtemisSource<T : Serializable>(val topic: String, val productedType: TypeInformation<T>) :
    SourceFunction<T>, ResultTypeQueryable<T> {

    override fun run(ctx: SourceFunction.SourceContext<T>) {
        val logger = logger()
        try {
            logger.enter()
            val consumerBackend = TopicConsumer<T>(topic)
            consumerBackend.register()
            consumerBackend.start()
            val i = consumerBackend
            while (consumerBackend.status == ServiceStatus.Started && i.hasNext()) {
                val v = i.next()
                ctx.collect(v)
            }
            consumerBackend.stop()
        } finally {
            logger.exit()
        }
    }

    override fun cancel() {
        TODO("Refactor consumerBackend to top level and cancel it from here, as in the code below - beware of serialization")
//        if (consumerBackend != null)
//            consumer().stop()
    }

    override fun getProducedType(): TypeInformation<T> = productedType


}
