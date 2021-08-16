package processm.experimental.etl.flink.artemis

import processm.core.log.Event

class DBEvent(conceptName: String) : Event() {
    override var conceptName: String? = conceptName
}
