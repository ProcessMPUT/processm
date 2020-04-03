package processm.core.models.causalnet

import processm.core.models.commons.AbstractModel
import processm.core.models.metadata.MetadataHandler

/**
 * A model instance, i.e., a composition of a read-only model and another source of metadata
 *
 * Model's metadata are to be treated as a expected, whereas instance's metadata are to be treated as actual
 */
abstract class ModelInstance(val model: Model, metadataHandler: MetadataHandler) :
    AbstractModel by model,
    MetadataHandler by metadataHandler