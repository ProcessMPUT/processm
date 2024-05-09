package processm.core.querylanguage

import org.antlr.v4.runtime.RecognitionException

internal class ProxyRecognitionException(
    val problem: PQLParserError.Problem,
    val offendingToken: TokenSequence,
    val expectedTokens: Collection<String>?,
) : RecognitionException(null, null, null)