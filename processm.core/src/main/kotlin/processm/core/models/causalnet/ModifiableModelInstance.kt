package processm.core.models.causalnet

import processm.core.models.metadata.BasicStatistics
import processm.core.models.metadata.DefaultMetadataProvider
import processm.core.models.metadata.IntMetadata
import processm.core.models.metadata.ModifiableMetadataHandler

/**
 * A modifiable model instance equipped with metadata providers corresponding to basic statistics
 */
class ModifiableModelInstance(model: Model, metadataHandler: ModifiableMetadataHandler) :
    ModelInstance(model, metadataHandler),
    ModifiableMetadataHandler by metadataHandler {

    init {
        for (name in BasicStatistics.BASIC_TIME_STATISTICS)
            addMetadataProvider(DefaultMetadataProvider<IntMetadata>(name))
    }

}