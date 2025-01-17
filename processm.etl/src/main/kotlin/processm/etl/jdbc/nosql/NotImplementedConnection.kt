package processm.etl.jdbc.nosql

import java.sql.*
import java.util.*
import java.util.concurrent.Executor

abstract class NotImplementedConnection : Connection {

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        intentionallyNotImplemented()
    }

    override fun createStatement(): Statement {
        intentionallyNotImplemented()
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int): Statement {
        intentionallyNotImplemented()
    }

    override fun createStatement(resultSetType: Int, resultSetConcurrency: Int, resultSetHoldability: Int): Statement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(sql: String?, resultSetType: Int, resultSetConcurrency: Int): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(sql: String?, autoGeneratedKeys: Int): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(sql: String?, columnIndexes: IntArray?): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareStatement(sql: String?, columnNames: Array<out String>?): PreparedStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(sql: String?): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(sql: String?, resultSetType: Int, resultSetConcurrency: Int): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun prepareCall(
        sql: String?,
        resultSetType: Int,
        resultSetConcurrency: Int,
        resultSetHoldability: Int
    ): CallableStatement {
        intentionallyNotImplemented()
    }

    override fun nativeSQL(sql: String?): String {
        intentionallyNotImplemented()
    }

    override fun setAutoCommit(autoCommit: Boolean) {
        intentionallyNotImplemented()
    }

    override fun getAutoCommit(): Boolean {
        intentionallyNotImplemented()
    }

    override fun commit() {
        intentionallyNotImplemented()
    }

    override fun rollback() {
        intentionallyNotImplemented()
    }

    override fun rollback(savepoint: Savepoint?) {
        intentionallyNotImplemented()
    }

    override fun isClosed(): Boolean {
        intentionallyNotImplemented()
    }

    override fun getMetaData(): DatabaseMetaData {
        intentionallyNotImplemented()
    }

    override fun setReadOnly(readOnly: Boolean) {
        intentionallyNotImplemented()
    }

    override fun isReadOnly(): Boolean {
        intentionallyNotImplemented()
    }

    override fun setCatalog(catalog: String?) {
        intentionallyNotImplemented()
    }

    override fun getCatalog(): String {
        intentionallyNotImplemented()
    }

    override fun setTransactionIsolation(level: Int) {
        intentionallyNotImplemented()
    }

    override fun getTransactionIsolation(): Int {
        intentionallyNotImplemented()
    }

    override fun getWarnings(): SQLWarning {
        intentionallyNotImplemented()
    }

    override fun clearWarnings() {
        intentionallyNotImplemented()
    }

    override fun getTypeMap(): MutableMap<String, Class<*>> {
        intentionallyNotImplemented()
    }

    override fun setTypeMap(map: MutableMap<String, Class<*>>?) {
        intentionallyNotImplemented()
    }

    override fun setHoldability(holdability: Int) {
        intentionallyNotImplemented()
    }

    override fun getHoldability(): Int {
        intentionallyNotImplemented()
    }

    override fun setSavepoint(): Savepoint {
        intentionallyNotImplemented()
    }

    override fun setSavepoint(name: String?): Savepoint {
        intentionallyNotImplemented()
    }

    override fun releaseSavepoint(savepoint: Savepoint?) {
        intentionallyNotImplemented()
    }

    override fun createClob(): Clob {
        intentionallyNotImplemented()
    }

    override fun createBlob(): Blob {
        intentionallyNotImplemented()
    }

    override fun createNClob(): NClob {
        intentionallyNotImplemented()
    }

    override fun createSQLXML(): SQLXML {
        intentionallyNotImplemented()
    }

    override fun isValid(timeout: Int): Boolean {
        intentionallyNotImplemented()
    }

    override fun setClientInfo(name: String?, value: String?) {
        intentionallyNotImplemented()
    }

    override fun setClientInfo(properties: Properties?) {
        intentionallyNotImplemented()
    }

    override fun getClientInfo(name: String?): String {
        intentionallyNotImplemented()
    }

    override fun getClientInfo(): Properties {
        intentionallyNotImplemented()
    }

    override fun createArrayOf(typeName: String?, elements: Array<out Any>?): java.sql.Array {
        intentionallyNotImplemented()
    }

    override fun createStruct(typeName: String?, attributes: Array<out Any>?): Struct {
        intentionallyNotImplemented()
    }

    override fun setSchema(schema: String?) {
        intentionallyNotImplemented()
    }

    override fun getSchema(): String {
        intentionallyNotImplemented()
    }

    override fun abort(executor: Executor?) {
        intentionallyNotImplemented()
    }

    override fun setNetworkTimeout(executor: Executor?, milliseconds: Int) {
        intentionallyNotImplemented()
    }

    override fun getNetworkTimeout(): Int {
        intentionallyNotImplemented()
    }

    protected fun intentionallyNotImplemented(): Nothing =
        throw SQLFeatureNotSupportedException("Intentionally not implemented")
}