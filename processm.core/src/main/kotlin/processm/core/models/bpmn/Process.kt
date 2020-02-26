package processm.core.models.bpmn

class Process(id: String, name: String) : CallableElement(id, name) {
    var processType = ProcessType.None
        internal set
    var isExecutable: Boolean? = null
        internal set
    var auditing: Auditing? = null
        internal set
    var monitoring: Monitoring? = null
        internal set
    val artifacts: List<Artifact> = ArrayList()
    var isClosed: Boolean = false
        internal set
    val supports: List<Process> = ArrayList()
    val properties: List<Property> = ArrayList()
    val resources: List<ResourceRole> = ArrayList()
    val correlationSubscriptions: List<CorrelationSubscription> = ArrayList()
    var definitionalCollaborationRef: Collaboration? = null
        internal set
}

//TODO consider Table 10.2 and the paragraph immediately before it