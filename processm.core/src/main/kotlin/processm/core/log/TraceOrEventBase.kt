package processm.core.log

import processm.core.log.attribute.MutableAttributeMap
import java.util.*

abstract class TraceOrEventBase(
    attributesInternal: MutableAttributeMap = MutableAttributeMap()
) : XESComponent(attributesInternal) {
    /**
     * Standard attribute based on concept:name
     * Standard extension: Concept
     */
    override var conceptName: String? = null
        internal set

    /**
     * Standard attribute based on cost:currency
     * Standard extension: Cost
     */
    var costCurrency: String? = null
        internal set

    /**
     * Backing field for [costTotal]. It consumes 8 bytes, in contrast to a Double?-typed field that would consume 24 bytes
     * if set, or 4 bytes if unset (assuming CompressedOops are on).
     * Note that Double.NaN.toRawBits() != -1L, and Double.fromBits(-1L) evaluates to Double.NaN.
     */
    private var _costTotal: Double = Double.fromBits(-1L)

    init {
        assert(Double.NaN.toRawBits() != -1L)
        assert(Double.fromBits(-1L).equals(Double.NaN))
        assert(Double.fromBits(-1L).toRawBits() == -1L)
    }

    /**
     * Standard attribute based on cost:total
     * Standard extension: Cost
     */
    var costTotal: Double?
        get() = if (_costTotal.toRawBits() == -1L) null else _costTotal
        internal set(value) {
            _costTotal = value ?: Double.fromBits(-1L)
        }

    /**
     * Standard attribute based on identity:id
     * Standard extension: Identity
     */
    override var identityId: UUID? = null
        internal set
}
