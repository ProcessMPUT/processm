package processm.core.models.bpmn

abstract class Activity {
    var isForCompensation: Boolean = false
        internal set
    var loopCharacteristics: LoopCharacteristics? = null
        internal set
    val resources: List<ResourceRole> = ArrayList()
    var default: SequenceFlow? = null
        internal set
    var ioSpecification: InputOutputSpecification? = null
        internal set
    val properties: List<Property> = ArrayList()
    val dataInputAssociations: List<DataInputAssociation> = ArrayList()
    val dataOutputAssociations: List<DataOutputAssociation> = ArrayList()
    var startQuantity: Int = 1
        internal set(v) = if (v >= 1) field = v else throw IllegalArgumentException()
    var completionQuantity: Int = 1
        internal set(v) = if (v >= 1) field = v else throw IllegalArgumentException()
}

//TODO consider Table 10.4 and the paragraph immediately preceeding it