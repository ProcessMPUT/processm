package processm.conformance.conceptdrift

interface DriftDetector<Artifact, ArtifactCollection> {

    /**
     * `true` if drift was observed, `false` otherwise.
     * If the value changes after a call to [observe], it does not imply that the artifact passed to the call is
     * responsible for the drift.
     */
    val drift: Boolean

    /**
     * Train the detector assuming there is no drift in [artifact], only outliers
     */
    fun fit(artifact: ArtifactCollection)

    /**
     * Record a new artifact, updating the underlying information and possibly changing [drift].
     *
     * @return The current value of [drift]
     */
    fun observe(artifact: Artifact): Boolean
}