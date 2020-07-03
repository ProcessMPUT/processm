package processm.core.querylanguage

import processm.core.logging.debug
import processm.core.logging.logger

/**
 * A dummy class for any expression that does not match other classes.
 */
class AnyExpression(
    val value: String,
    override val line: Int,
    override val charPositionInLine: Int
) : Expression() {

    companion object {
        private val logger = logger()
    }

    init {
        logger.debug {
            "Line $line position $charPositionInLine: Use of a dummy expression in the PQL parser for the string \"$value\". Replace it with a designated class."
        }
    }

    override fun toString(): String = value
}