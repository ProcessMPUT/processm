package processm.services.helpers

import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class DataConnectorHelpersTest {

    @Test
    fun `masking throws for unsupported url`() {
        assertThrows<IllegalArgumentException> { maskPasswordInJdbcUrl("http://processm.cs.put.poznan.pl/") }
    }

    @Test
    fun `postgres - password`() {
        val url = "jdbc:postgresql://localhost/test?user=fred&password=secret&ssl=true"
        assertEquals(
            "jdbc:postgresql://localhost/test?user=fred&password=MASK&ssl=true",
            maskPasswordInJdbcUrl(url, "MASK")
        )
    }

    @Test
    fun `postgres - sslpassword`() {
        val url = "jdbc:postgresql://localhost/test?user=fred&sslpassword=secret&ssl=true"
        assertEquals(
            "jdbc:postgresql://localhost/test?user=fred&sslpassword=MASK&ssl=true",
            maskPasswordInJdbcUrl(url, "MASK")
        )
    }

    @Test
    fun `postgres - many passwords`() {
        val url = "jdbc:postgresql://localhost/test?password=foo&sslpassword=secret&ssl=true&password=bar"
        assertEquals(
            "jdbc:postgresql://localhost/test?password=MASK&sslpassword=MASK&ssl=true&password=MASK",
            maskPasswordInJdbcUrl(url, "MASK")
        )
    }

    @Test
    fun `postgres - notapassword`() {
        val url = "jdbc:postgresql://localhost/test?user=fred&notapassword=secret&ssl=true"
        assertEquals(url, maskPasswordInJdbcUrl(url))
    }

    @Test
    fun `postgres - only question mark`() {
        val url = "jdbc:postgresql://localhost/test?"
        assertEquals(url, maskPasswordInJdbcUrl(url))
    }

    @Test
    fun `postgres - no query`() {
        val url = "jdbc:postgresql://localhost/test"
        assertEquals(url, maskPasswordInJdbcUrl(url))
    }

    /**
     * https://www.baeldung.com/java-jdbc-url-format#2-hosts
     */
    @Test
    fun `mysql - properties 2`() {
        assertEquals(
            "jdbc:mysql:loadbalance://myhost1:3306,myhost2:3307/db_name?user=dbUser&password=MASK&loadBalanceConnectionGroup=group_name&ha.enableJMX=true",
            maskPasswordInJdbcUrl(
                "jdbc:mysql:loadbalance://myhost1:3306,myhost2:3307/db_name?user=dbUser&password=1234567&loadBalanceConnectionGroup=group_name&ha.enableJMX=true",
                "MASK"
            )
        )
    }

    /**
     * https://www.baeldung.com/java-jdbc-url-format#3-properties-and-user-credentials
     */
    @Test
    fun `mysql - properties`() {
        assertEquals(
            "jdbc:mysql://myhost1:3306/db_name?user=root&password=MASK",
            maskPasswordInJdbcUrl("jdbc:mysql://myhost1:3306/db_name?user=root&password=mypass", "MASK")
        )
    }

    /**
     * https://www.baeldung.com/java-jdbc-url-format#3-properties-and-user-credentials
     */
    @Test
    fun `mysql - prefix`() {
        assertEquals(
            "jdbc:mysql://root:MASK@myhost1:3306/db_name",
            maskPasswordInJdbcUrl("jdbc:mysql://root:mypass@myhost1:3306/db_name", "MASK")
        )
    }

    @Test
    fun `mysql - prefix at a sublist of simple hosts`() {
        assertEquals(
            "jdbc:mysql://root:MASK@[myhost1:3306,myhost2:3307]/db_name",
            maskPasswordInJdbcUrl("jdbc:mysql://root:mypass@[myhost1:3306,myhost2:3307]/db_name", "MASK")
        )
    }

    /**
     * https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html
     */
    @Test
    fun `mysql - prefix at a sublist with address equals hosts`() {
        assertEquals(
            "mysqlx://sandy:MASK@[(address=host1:1111,priority=1,key1=value1),(address=host2:2222,priority=2,key2=value2)]/db",
            maskPasswordInJdbcUrl(
                "mysqlx://sandy:secret@[(address=host1:1111,priority=1,key1=value1),(address=host2:2222,priority=2,key2=value2)]/db",
                "MASK"
            )
        )
    }

    /**
     * https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html
     */
    @Test
    fun `mysql - two key-value hosts inside a sublist`() {
        assertEquals(
            "jdbc:mysql://[(host=myhost1,port=1111,user=sandy,password=MASK),(host=myhost2,port=2222,user=finn,password=MASK)]/db",
            maskPasswordInJdbcUrl(
                "jdbc:mysql://[(host=myhost1,port=1111,user=sandy,password=secret),(host=myhost2,port=2222,user=finn,password=secret)]/db",
                "MASK"
            )
        )
    }

    /**
     * https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html
     */
    @Test
    fun `mysql - two key-value hosts`() {
        assertEquals(
            "jdbc:mysql://address=(host=myhost1)(port=1111)(user=sandy)(password=MASK),address=(host=myhost2)(port=2222)(user=finn)(password=MASK)/db",
            maskPasswordInJdbcUrl(
                "jdbc:mysql://address=(host=myhost1)(port=1111)(user=sandy)(password=secret),address=(host=myhost2)(port=2222)(user=finn)(password=secret)/db",
                "MASK"
            )
        )
    }

    @Test
    fun `mysql - prefix within the host list`() {
        assertEquals(
            "jdbc:mysql://localhost:3306,test:MASK@[(host=localhost,port=3306,user=sandy,password=MASK),(host=localhost,port=3306,user=finn,password=MASK)]/db",
            maskPasswordInJdbcUrl(
                "jdbc:mysql://localhost:3306,test:test@[(host=localhost,port=3306,user=sandy,password=secret),(host=localhost,port=3306,user=finn,password=secret)]/db",
                "MASK"
            )
        )
    }

    @Test
    fun `mysql - address-equals with prefix`() {
        assertEquals(
            "jdbc:mysql://test:MASK@address=(host=myhost)(port=1111)(key1=value1)/db",
            maskPasswordInJdbcUrl(
                "jdbc:mysql://test:passworth@address=(host=myhost)(port=1111)(key1=value1)/db",
                "MASK"
            )
        )
    }

    @Test
    fun `sqlserver no optional parts`() {
        assertEquals("jdbc:sqlserver://", maskPasswordInJdbcUrl("jdbc:sqlserver://"))
    }

    @Test
    fun `sqlserver simple`() {
        assertEquals(
            "jdbc:sqlserver://localhost;encrypt=true;user=MyUserName;password=MASK;",
            maskPasswordInJdbcUrl(
                "jdbc:sqlserver://localhost;encrypt=true;user=MyUserName;password=loreipsum;",
                "MASK"
            )
        )
    }

    /**
     * Before version 8.4, escaped values can contain special characters (especially =, ;, [], and space) but can't contain braces. Values that must be escaped and contain braces should be added to a properties collection.
     *
     * In version 8.4 and above, escaped values can contain special characters, including braces. However, closing braces must be escaped. For example, with a password of pass";{}word, a connection string would need to escape the password as follows:
     *
     * jdbc:sqlserver://localhost;encrypt=true;username=MyUsername;password={pass";{}}word};
     *
     * https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url?view=sql-server-ver16
     */
    @Test
    fun `sqlserver escape`() {
        assertEquals(
            "jdbc:sqlserver://localhost;encrypt=true;username=MyUsername;password=MASK;",
            maskPasswordInJdbcUrl(
                "jdbc:sqlserver://localhost;encrypt=true;username=MyUsername;password={pass\";{}}word};",
                "MASK"
            )
        )
    }

    @Test
    fun `sqlserver ipv6 and instance`() {
        val url =
            "jdbc:sqlserver://;serverName=3ffe:8311:eeee:f70f:0:5eae:10.203.31.9\\\\instance1;encrypt=true;integratedSecurity=true;"
        assertEquals(url, maskPasswordInJdbcUrl(url))
    }

    /**
     * Example from https://docs.oracle.com/en/database/oracle/oracle-database/21/jajdb/oracle/jdbc/OracleDriver.html
     */
    @Test
    fun `oracle - thin style`() {
        assertEquals(
            "jdbc:oracle:thin:myuser/MASK@//salesserver1:1521/sales.us.example.com",
            maskPasswordInJdbcUrl("jdbc:oracle:thin:myuser/mypassword@//salesserver1:1521/sales.us.example.com", "MASK")
        )
    }

    @Test
    fun `oracle - descriptor - no password`() {
        val url = """jdbc:oracle:thin:@(DESCRIPTION=
  (LOAD_BALANCE=on)
(ADDRESS_LIST=
  (ADDRESS=(PROTOCOL=TCP)(HOST=host1) (PORT=5221))
 (ADDRESS=(PROTOCOL=TCP)(HOST=host2)(PORT=5221)))
 (CONNECT_DATA=(SERVICE_NAME=orcl)))"""
        assertEquals(url, maskPasswordInJdbcUrl(url))
    }

    /**
     * https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/client-side-security.html#GUID-62AD3F23-21B5-49D3-8325-313267444ADD
     */
    @Test
    fun `oracle - thin style - access token`() {
        assertEquals(
            "jdbc:oracle:thin:@tcps://adb.mydomain.oraclecloud.com:1522/xyz.adb.oraclecloud.com?oracle.jdbc.accessToken=MASK",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:@tcps://adb.mydomain.oraclecloud.com:1522/xyz.adb.oraclecloud.com?oracle.jdbc.accessToken=\"ey...5c\"",
                "MASK"
            )
        )
    }

    /**
     * https://docs.oracle.com/en/database/oracle/oracle-database/21/jjdbc/client-side-security.html#GUID-62AD3F23-21B5-49D3-8325-313267444ADD
     */
    @Test
    fun `oracle - descriptor - access token`() {
        assertEquals(
            """jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=tcps)(HOST=mydomain.com)(PORT=5525))
      (CONNECT_DATA=(SERVICE_NAME=myservice.com)))?
        oracle.jdbc.accessToken=MASK""",
            maskPasswordInJdbcUrl(
                """jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=tcps)(HOST=mydomain.com)(PORT=5525))
      (CONNECT_DATA=(SERVICE_NAME=myservice.com)))?
        oracle.jdbc.accessToken="ey...5c"""",
                "MASK"
            )
        )
    }

    /**
     * I couldn't find this style of specifying credentials in the doc, but it seemed obvious that it should be possible,
     * and in fact it works fine.
     *
     * I suppose it is a combination of Section 8.2.5.4 and Table 8-1 from [1]
     *
     * [1] https://docs.oracle.com/en/database/oracle/oracle-database/23/jjdbc/data-sources-and-URLs.html#GUID-F0A0C45C-C5E9-45B6-AB86-181218D348CB
     */
    @Test
    fun `oracle - thin style - password in query`() {
        assertEquals(
            "jdbc:oracle:thin:@//localhost:1521/xe?user=system&password=MASK",
            maskPasswordInJdbcUrl("jdbc:oracle:thin:@//localhost:1521/xe?user=system&password=loreipsum123", "MASK")
        )
    }

    /**
     * https://docs.oracle.com/en/database/oracle/oracle-database/23/jajdb/oracle/jdbc/OracleConnection.html
     *
     * I tested it - it works
     */
    @Test
    fun `oracle - thin style - password in query v2`() {
        assertEquals(
            "jdbc:oracle:thin:@//localhost:1521/xe?user=system&oracle.jdbc.password=MASK",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:@//localhost:1521/xe?user=system&oracle.jdbc.password=loreipsum123",
                "MASK"
            )
        )
    }


    /**
     * Dunno whether this is intended or not, but it works
     */
    @Test
    fun `oracle - description with extended settings`() {
        assertEquals(
            "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=tcp)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=xe)))?user=system&oracle.jdbc.password=MASK",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:@(DESCRIPTION=(ADDRESS=(PROTOCOL=tcp)(HOST=localhost)(PORT=1521))(CONNECT_DATA=(SERVICE_NAME=xe)))?user=system&oracle.jdbc.password=loreipsum123",
                "MASK"
            )
        )
    }

    @Test
    fun `oracle - thin style - password with @`() {
        assertEquals(
            "jdbc:oracle:thin:system/MASK@//localhost:1521/xe",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:system/\"loreip@sum123\"@//localhost:1521/xe",
                "MASK"
            )
        )
    }

    @Test
    fun `oracle - thin style - password in the query with &`() {
        assertEquals(
            "jdbc:oracle:thin:@//localhost:1521/xe?user=system&password=MASK",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:@//localhost:1521/xe?user=system&password=\"loreip&sum123\"",
                "MASK"
            )
        )
    }

    @Test
    fun `oracle - thin style - key in the query with the quotation marks`() {
        assertEquals(
            "jdbc:oracle:thin:@//localhost:1521/xe?u\"\"ser=system&\"pass\"\"word\"=MASK",
            maskPasswordInJdbcUrl(
                "jdbc:oracle:thin:@//localhost:1521/xe?u\"\"ser=system&\"pass\"\"word\"=\"blah\"",
                "MASK"
            )
        )
    }

    @Test
    fun `db2 - password last`() {
        assertEquals(
            "jdbc:db2://localhost:50000/testdb:user=db2inst1;password=MASK;",
            maskPasswordInJdbcUrl("jdbc:db2://localhost:50000/testdb:user=db2inst1;password=password;", "MASK")
        )
    }

    @Test
    fun `db2 - password first`() {
        assertEquals(
            "jdbc:db2://localhost:50000/testdb:password=MASK;user=db2inst1;",
            maskPasswordInJdbcUrl("jdbc:db2://localhost:50000/testdb:password=password;user=db2inst1;", "MASK")
        )
    }

    @Test
    fun `couchdb - no password`() {
        assertEquals("couchdb:http://localhost:12345/foo", maskPasswordInJdbcUrl("couchdb:http://localhost:12345/foo"))
    }

    @Test
    fun `couchdb - password`() {
        assertEquals(
            "couchdb:http://user:MASK@localhost:12345/foo",
            maskPasswordInJdbcUrl("couchdb:http://user:passworth@localhost:12345/foo", "MASK")
        )
    }

    @Test
    fun `mongo - srv, single server`() {
        assertEquals(
            "mongodb+srv://myDatabaseUser:MASK@cluster0.example.mongodb.net/?retryWrites=true&w=majority",
            maskPasswordInJdbcUrl(
                "mongodb+srv://myDatabaseUser:D1fficultP%40ssw0rd@cluster0.example.mongodb.net/?retryWrites=true&w=majority",
                "MASK"
            )
        )
    }

    @Test
    fun `mongo - standalone, multiple servers`() {
        assertEquals(
            "mongodb://myDatabaseUser:MASK@mongodb0.example.com:27017,mongodb1.example.com:27017,mongodb2.example.com:27017/?authSource=admin&replicaSet=myRepl",
            maskPasswordInJdbcUrl(
                "mongodb://myDatabaseUser:D1fficultP%40ssw0rd@mongodb0.example.com:27017,mongodb1.example.com:27017,mongodb2.example.com:27017/?authSource=admin&replicaSet=myRepl",
                "MASK"
            )
        )
    }
}