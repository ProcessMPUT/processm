package processm.core.models.metadata

data class URN(val urn: String) {
    init {
        if (!reURN.matches(urn))
            throw IllegalArgumentException()
    }

    companion object {
        /**
         * Poor's man grammar. Order is important to this extent that a definition must come after all usages
         */
        private val grammar = listOf(
            "<nid>" to "(?:\\p{Alnum}<ldh>{0,30}\\p{Alnum})", //NID           = (alphanum) 0*30(ldh) (alphanum)
            "<nss>" to "<pchar>(?:<pchar>|/)*", //NSS           = pchar *(pchar / "/")
            "<rcomponent>" to "<pchar>(?:<pchar>|/|\\?)*",    // r-component   = pchar *( pchar / "/" / "?" )
            "<qcomponent>" to "<pchar>(?:<pchar>|/|\\?)*",    // q-component   = pchar *( pchar / "/" / "?" )
            "<fcomponent>" to "<fragment>",  //f-component   = fragment
            "<fragment>" to "(?:<pchar>|/|\\?)*",    //fragment      = *( pchar / "/" / "?" )
            "<ldh>" to "[\\p{Alnum}-]", //ldh           = alphanum / "-"
            "<pchar>" to "(?:<unreserved>|<pctencoded>|<subdelims>|:|@)", // pchar         = unreserved / pct-encoded / sub-delims / ":" / "@"
            "<unreserved>" to "[\\p{Alnum}._~-]", //unreserved    = ALPHA / DIGIT / "-" / "." / "_" / "~"
            "<pctencoded>" to "(?:%\\p{XDigit}\\p{XDigit})", //pct-encoded   = "%" HEXDIG HEXDIG
            "<subdelims>" to "[!$&'()*+,;=]" //sub-delims    = "!" / "$" / "&" / "'" / "(" / ")" / "*" / "+" / "," / ";" / "="
        )
        /**
         * Based on https://tools.ietf.org/html/rfc8141#section-2
         */
        /*
          namestring    = assigned-name
              [ rq-components ]
              [ "#" f-component ]
        assigned-name = "urn" ":" NID ":" NSS
        rq-components = [ "?+" r-component ]
                      [ "?=" q-component ]
        */
        private val reURN = Regex(
            grammar.fold(
                "^urn:<nid>:<nss>(\\?+<rcomponent>)?(\\?=<qcomponent>)?(#<fcomponent>)?$",
                { re, (old, new) -> re.replace(old, new) }),
            RegexOption.IGNORE_CASE
        )

    }
}