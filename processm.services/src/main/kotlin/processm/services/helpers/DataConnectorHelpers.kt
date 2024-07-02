package processm.services.helpers

const val defaultPasswordMask = "********"

/**
 * Replace passwords, tokens and secrets in [url] with [passwordMask]. Since there is no single syntax for JDBC URLs, and
 * database manuals not necessarily explicitly formalize all the details of the syntax, it is possible that
 * a) in some cases a value that should be masked will not be masked;
 * b) a URL that is handled correctly by the driver will cause a malfunction.
 *
 * It is thus recommended that:
 * 1. [passwordMask] does not contain any special characters, as not to confuse the user or create problems should they forget to replace the mask with an actual password
 * 2. Any exception thrown is handled by logging a warning and returning the non-masked to the user
 */
fun maskPasswordInJdbcUrl(url: String, passwordMask: String = defaultPasswordMask): String =
    when {
        url.startsWith("jdbc:oracle:", ignoreCase = true) -> maskOracle(url, passwordMask)
        url.startsWith("jdbc:postgresql:", ignoreCase = true) -> maskPostgres(url, passwordMask)
        url.startsWith("jdbc:mysql:", ignoreCase = true) -> maskMysql(url, passwordMask)
        url.startsWith("mysqlx:", ignoreCase = true) -> maskMysql(url, passwordMask)
        url.startsWith("jdbc:sqlserver://", ignoreCase = true) -> maskSqlServer(url, passwordMask)
        url.startsWith("jdbc:oracle:", ignoreCase = true) -> maskOracle(url, passwordMask)
        db2Prefixes.any { url.startsWith(it, ignoreCase = true) } -> maskDb2(url, passwordMask)
        else -> throw IllegalArgumentException()
    }

// region shared helpers

private data class KeyValue(val key: String, var value: String?)

private fun queryToPairs(query: String): List<KeyValue?> = query.split('&').mapTo(ArrayList()) {
    val kv = it.split("=", limit = 2)
    when {
        kv.isEmpty() -> null
        kv.size == 1 -> KeyValue(kv[0], null)
        else -> KeyValue(kv[0], kv[1])
    }
}

private fun StringBuilder.appendQuery(pairs: List<KeyValue?>) {
    assert(pairs.isNotEmpty())
    for (pair in pairs) {
        if (pair !== null) {
            append(pair.key)
            pair.value?.let {
                append("=")
                append(pair.value)
            }
        }
        append("&")
    }
    deleteCharAt(length - 1)
}

// endregion

// region MySQL

/**
 * From: https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html
 *
 * protocol//[hosts][/database][?properties]
 *
 * > Any reserved characters for URLs (for example, /, :, @, (, ), [, ], &, #, =, ?, and space) that appear in any part of the connection URL must be percent encoded.
 *
 * Hence, splitting by / and ? is fine
 */
private val reMysqlGenericFormat = Regex("^(?<protocol>.*?//)(?<hosts>.*?)?(?<database>/.*?)?(?<properties>\\?.*?)?$")

/**
 * https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html
 *
 * Any reserved characters for URLs (for example, /, :, @, (, ), [, ], &, #, =, ?, and space) that appear in any part of the connection URL must be percent encoded.
 */
private fun maskMysql(url: String, passwordMask: String): String {
    require('&' !in passwordMask)
    val match = checkNotNull(reMysqlGenericFormat.matchEntire(url))
    return buildString {
        append(match.groups["protocol"]!!.value)
        match.groups["hosts"]?.let { maskMysqlHosts(it.value, passwordMask) }
        match.groups["database"]?.let { append(it.value) }
        match.groups["properties"]?.let { maskMysqlProperties(it.value, passwordMask) }
    }
}

private val reMysqlCredentialsPrefix = Regex("(?<user>[^:@,]*?:)(?<password>[^@,]*?)@")

private val reMysqlSimpleHostSpecification = Regex("[^():]+:\\d+")

private val reMysqlAddressEqualsHostSpecification = Regex("address=(\\(.*?\\))+", RegexOption.IGNORE_CASE)
private val reMysqlKeyValueHostSpecification = Regex("\\(.*?\\)")

private fun StringBuilder.maskMysqlHosts(hosts: String, passwordMask: String) {
    var idx = 0
    while (idx < hosts.length) {
        if (hosts[idx] == '[' || hosts[idx] == ']' || hosts[idx] == ',') {
            append(hosts[idx])
            idx++
            continue
        }
        val mPrefix = reMysqlCredentialsPrefix.matchAt(hosts, idx)
        if (mPrefix !== null) {
            append(mPrefix.groups["user"]!!.value)
            append(passwordMask)
            append("@")
            idx = mPrefix.range.last + 1
            continue
        }
        val mSimple = reMysqlSimpleHostSpecification.matchAt(hosts, idx)
        if (mSimple !== null) {
            append(mSimple.value)
            idx = mSimple.range.last + 1
            continue
        }
        val mAddress = reMysqlAddressEqualsHostSpecification.matchAt(hosts, idx)
        if (mAddress !== null) {
            maskMysqlAddressHost(mAddress.value, passwordMask)
            idx = mAddress.range.last + 1
            continue
        }
        val mKeyValue = reMysqlKeyValueHostSpecification.matchAt(hosts, idx)
        if (mKeyValue !== null) {
            maskMysqlKeyValueHost(mKeyValue.value, passwordMask)
            idx = mKeyValue.range.last + 1
            continue
        }
        throw IllegalArgumentException(
            "Part of the host specification is neither simple nor address-equals, nor key-value: `${hosts.substring(idx)}` `$hosts`"
        )
    }
}

private fun StringBuilder.maskMysqlAddressHost(host: String, passwordMask: String) {
    val prefix = "address="
    assert(host.startsWith(prefix, ignoreCase = true))
    var idx = prefix.length
    append(host.substring(0, idx))
    while (idx < host.length) {
        check(host[idx] == '(')
        val eq = host.indexOf('=', idx + 1)
        check(eq > idx)
        val end = host.indexOf(')', eq + 1)
        check(end > eq)
        val key = host.substring(idx + 1, eq)
        if (mysqlPasswordProperties.any { it.equals(key, ignoreCase = true) }) {
            append("(")
            append(key)
            append("=")
            append(passwordMask)
            append(")")
        } else
            append(host.substring(idx, end + 1))
        idx = end + 1
    }
}

private fun StringBuilder.maskMysqlKeyValueHost(host: String, passwordMask: String) {
    assert(host[0] == '(')
    assert(host.last() == ')')
    var idx = 1
    append("(")
    while (idx < host.length - 1) {
        val eq = host.indexOf('=', idx + 1)
        check(eq > idx)
        val end = host.indexOf(',', eq + 1).takeIf { it >= 0 } ?: (host.length - 1)
        val key = host.substring(idx, eq)
        if (mysqlPasswordProperties.any { it.equals(key, ignoreCase = true) }) {
            append(key)
            append("=")
            append(passwordMask)
        } else
            append(host.substring(idx, end))
        idx = end + 1
        append(",")
    }
    deleteCharAt(length - 1)
    append(")")
}

// https://dev.mysql.com/doc/connector-j/en/connector-j-reference-configuration-properties.html
private val mysqlPasswordProperties = listOf(
    "password",
    "password1",
    "password2",
    "password3",
    "trustCertificateKeyStorePassword",
    "clientCertificateKeyStorePassword",
    "xdevapi.ssl-keystore-password",
    "xdevapi.ssl-truststore-password"
)

private fun StringBuilder.maskMysqlProperties(properties: String, passwordMask: String) {
    assert(properties[0] == '?')
    append("?")
    val query = queryToPairs(properties.substring(1))
    for (kv in query)
        if (kv !== null && kv.value !== null && mysqlPasswordProperties.any { it.equals(kv.key, true) })
            kv.value = passwordMask
    appendQuery(query)
}

// endregion

// region Postgres

/**
 * https://jdbc.postgresql.org/documentation/use/ claims that "Any reserved characters for URLs (for example, /, :, @, (, ), [, ], &, #, =, ?, and space) (...) must be percent-encoded", so splitting and searching by them seems safe
 */
private fun maskPostgres(url: String, passwordMask: String): String {
    require('&' !in passwordMask)
    val parts = url.split("?", limit = 2)
    if (parts.size < 2)
        return url
    val query = queryToPairs(parts[1])
    for (kv in query)
        if (kv !== null && kv.value !== null && ("password".equals(kv.key, true) || "sslpassword".equals(kv.key, true)))
            kv.value = passwordMask
    return buildString {
        append(parts[0])
        append("?")
        appendQuery(query)
    }
}

// endregion

// region SQL Server

private val sqlServerPasswordProperties =
    listOf("clientKeyPassword", "keyStoreSecret", "password", "trustStorePassword")

/**
 * jdbc:sqlserver://[serverName[\instanceName][:portNumber]][;property=value[;property=value]]
 */
private fun maskSqlServer(url: String, passwordMask: String): String {
    var start = url.indexOf(';')
    if (start < 0)
        return url
    start++
    return buildString {
        append(url.substring(0, start))
        // true if we are inside an escape sequence delimited by { }
        var escape = false
        // the index where the key of the current property starts
        var keyStartIdx: Int = start
        // the index where the key of the current property ends (i.e., where = was encountered)
        var keyEndIdx: Int? = null
        // true if are in a sequence of closing braces and we have seen an odd number of them
        var oddNumberOfClosingBraces = false
        for (idx in start until url.length) {
            if (url[idx] == '}') {
                oddNumberOfClosingBraces = !oddNumberOfClosingBraces
                continue
            }
            if (escape) {
                if (oddNumberOfClosingBraces)
                    escape = false
                else
                    continue
            }
            oddNumberOfClosingBraces = false
            if (url[idx] == '{') {
                escape = true
                continue
            }
            if (keyEndIdx == null && url[idx] == '=') {
                keyEndIdx = idx
                continue
            }
            if (url[idx] == ';' || idx == url.length - 1) {
                if (keyEndIdx !== null) {
                    val key = url.substring(keyStartIdx, keyEndIdx)
                    if (sqlServerPasswordProperties.any { it.equals(key, ignoreCase = true) }) {
                        append(key)
                        append("=")
                        append(passwordMask)
                        if (url[idx] == ';') append(';')
                        keyStartIdx = idx + 1
                        keyEndIdx = null
                        continue
                    }
                }
                append(url.substring(keyStartIdx, idx + 1))
                keyStartIdx = idx + 1
                keyEndIdx = null
            }
        }
    }
}

// endregion

// region Oracle

/**
 * According to https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/data-sources-and-URLs.html#GUID-C4F2CA86-0F68-400C-95DA-30171C9FB8F0
 *
 * jdbc:oracle:driver_type:[username/password]@database_specifier
 */
private fun maskOracle(url: String, passwordMask: String): String {
    require('@' !in passwordMask)
    require('"' !in passwordMask)
    require('\\' !in passwordMask)
    val reader = OracleReader(url, 0)
    return buildString {
        // jdbc:oracle:something:
        repeat(3) {
            reader.readEscaped(':')
            append(reader.rawPiece)
        }
        reader.readEscaped('/', '@')
        append(reader.rawPiece)
        if (reader.lastRead == '/') {
            reader.readEscaped('@')
            append(passwordMask)
            append('@')
        }
        reader.readParenthesized('?')
        append(reader.rawPiece)
        if (!reader.end)
            maskOracleQuery(reader, passwordMask)
    }
}

private val oraclePasswordProperties =
    listOf(
        "password",
        "oracle.jdbc.password",
        "oracle.jdbc.newPassword",
        "oracle.jdbc.clientCertificatePassword",
        "oracle.jdbc.clientSecret",
        "oracle.net.wallet_password",
        "javax.net.ssl.keyStorePassword",
        "javax.net.ssl.trustStorePassword",
        "oracle.jdbc.accessToken"
    )

/**
 * Partial parser for Oracle JDBC URL
 */
internal class OracleReader(val text: String, start: Int) {
    private var idx = start
    private var escape = false
    private var quot = false
    private val piece = StringBuilder()
    private var pieceStart: Int = start
    val rawPiece: String
        get() = text.substring(pieceStart, idx)

    val lastRead: Char
        get() = text[idx - 1]

    val end: Boolean
        get() = idx >= text.length

    fun readParenthesized(c: Char) {
        piece.clear()
        pieceStart = idx
        // inspired by oracle.net.resolver.findExtendedSettingPosition
        var parenthesis = 0
        while (idx < text.length) {
            when (text[idx++]) {
                '(' -> parenthesis++
                ')' -> parenthesis--
                c -> break
            }
        }
    }

    /**
     * Oracle doesn't do URL-encode. Instead, values with special characters need to be wrapped in ", and " is escaped using \
     */
    fun readEscaped(char1: Char, char2: Char? = null): String {
        piece.clear()
        pieceStart = idx
        while (idx < text.length) {
            val c = text[idx++]
            if (escape) {
                piece.append(c)
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                quot = !quot
                continue
            }
            if (quot) {
                piece.append(c)
                continue
            }
            if (c == char1 || c == char2)
                break
            piece.append(c)
        }
        return piece.toString()
    }
}


private fun StringBuilder.maskOracleQuery(reader: OracleReader, passwordMask: String) {
    var password = false
    var key = true
    while (!reader.end) {
        if (key) {
            val piece = reader.readEscaped('=', '&').trim()
            if (reader.lastRead == '=') {
                key = false
                password = oraclePasswordProperties.any { it.equals(piece, ignoreCase = true) }
            }
            append(reader.rawPiece)
        } else {
            reader.readEscaped('&')
            if (password)
                append(passwordMask)
            else
                append(reader.rawPiece)
            password = false
            key = true
        }
    }
}

// endregion

// region DB2

// https://www.ibm.com/docs/en/db2/11.5?topic=cdsudidsdjs-url-format-data-server-driver-jdbc-sqlj-type-2-connectivity also specifies "jdbc:default". I am reluctant to include it here.
private val db2Prefixes = listOf("jdbc:db2", "jdbc:ids:", "jdbc:ibmdb:")

private val reDb2Password = Regex(
    "([;:.](?:password|sslTrustStorePassword|accessToken|apiKey|sslKeyStorePassword)=).*?;",
    setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
)

/**
 * https://www.ibm.com/docs/en/db2/11.5?topic=cdsudidsdjs-url-format-data-server-driver-jdbc-sqlj-type-4-connectivity
 * https://www.ibm.com/docs/en/db2/11.5?topic=cdsudidsdjs-url-format-data-server-driver-jdbc-sqlj-type-2-connectivity
 *
 * The doc sounds like it is impossible to escape special characters in the url, hence a simple approach employing
 * a regex will suffice except in some corner cases situations, like a database name (which can be a file path if connecting
 * to IBM Cloudscape AKA Apache Derby) matching the regex. I think it is not worth the effort to handle it given that there's
 * much greater problem of the lack of support for some special characters in passwords, which would get accepted if password
 * was not passed in the URL, but as a separate property.
 */
private fun maskDb2(url: String, passwordMask: String): String {
    val replacement = "$1${Regex.escapeReplacement(passwordMask)};"
    return reDb2Password.replace(url, replacement)
}

// endregion