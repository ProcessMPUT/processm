package processm.core.models.causalnet

import processm.core.models.metadata.BasicStatistics
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.IntMetadata
import processm.core.models.metadata.MutableMetadataHandler

/**
 * A mutable model instance equipped with metadata providers corresponding to basic statistics
 */
class MutableModelInstance(model: Model, metadataHandler: MutableMetadataHandler) :
    ModelInstance(model, metadataHandler),
    MutableMetadataHandler by metadataHandler {

    init {
        for (name in BasicStatistics.BASIC_TIME_STATISTICS)
            addMetadataProvider(DefaultMetadataProvider<IntMetadata>(name))
    }

}