package processm.core.querylanguage

import org.antlr.v4.runtime.RecognitionException

/**
 * An auxiliary class to temporarily wrap information necessary to create [PQLParserException] as a [RecognitionException]
 */
internal class ProxyRecognitionException(
    val problem: PQLParserException.Problem,
    val offendingToken: TokenSequence,
    val expectedTokens: Collection<String>?,
) : RecognitionException(null, null, null)