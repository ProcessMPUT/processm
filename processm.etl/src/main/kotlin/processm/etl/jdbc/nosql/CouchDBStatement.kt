package processm.etl.jdbc.nosql

import java.sql.*

internal open class CouchDBStatement : Statement {

    private fun intentionallyNotImplemented(): Nothing =
        throw SQLFeatureNotSupportedException("Intentionally not implemented")

    override fun <T : Any?> unwrap(p0: Class<T>?): T {
        intentionallyNotImplemented()
    }

    override fun isWrapperFor(p0: Class<*>?): Boolean {
        intentionallyNotImplemented()
    }

    override fun close() {
    }

    override fun executeQuery(p0: String?): ResultSet {
        intentionallyNotImplemented()
    }

    override fun executeUpdate(p0: String?): Int {
        intentionallyNotImplemented()
    }

    override fun executeUpdate(p0: String?, p1: Int): Int {
        intentionallyNotImplemented()
    }

    override fun executeUpdate(p0: String?, p1: IntArray?): Int {
        intentionallyNotImplemented()
    }

    override fun executeUpdate(p0: String?, p1: Array<out String>?): Int {
        intentionallyNotImplemented()
    }

    override fun getMaxFieldSize(): Int {
        intentionallyNotImplemented()
    }

    override fun setMaxFieldSize(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getMaxRows(): Int {
        intentionallyNotImplemented()
    }

    override fun setMaxRows(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun setEscapeProcessing(p0: Boolean) {
        intentionallyNotImplemented()
    }

    override fun getQueryTimeout(): Int {
        intentionallyNotImplemented()
    }

    override fun setQueryTimeout(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun cancel() {
        intentionallyNotImplemented()
    }

    override fun getWarnings(): SQLWarning {
        intentionallyNotImplemented()
    }

    override fun clearWarnings() {
        intentionallyNotImplemented()
    }

    override fun setCursorName(p0: String?) {
        intentionallyNotImplemented()
    }

    override fun execute(p0: String?): Boolean {
        intentionallyNotImplemented()
    }

    override fun execute(p0: String?, p1: Int): Boolean {
        intentionallyNotImplemented()
    }

    override fun execute(p0: String?, p1: IntArray?): Boolean {
        intentionallyNotImplemented()
    }

    override fun execute(p0: String?, p1: Array<out String>?): Boolean {
        intentionallyNotImplemented()
    }

    override fun getResultSet(): ResultSet {
        intentionallyNotImplemented()
    }

    override fun getUpdateCount(): Int {
        intentionallyNotImplemented()
    }

    override fun getMoreResults(): Boolean {
        intentionallyNotImplemented()
    }

    override fun getMoreResults(p0: Int): Boolean {
        intentionallyNotImplemented()
    }

    override fun setFetchDirection(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getFetchDirection(): Int {
        intentionallyNotImplemented()
    }

    override fun setFetchSize(p0: Int) {
        intentionallyNotImplemented()
    }

    override fun getFetchSize(): Int {
        intentionallyNotImplemented()
    }

    override fun getResultSetConcurrency(): Int {
        intentionallyNotImplemented()
    }

    override fun getResultSetType(): Int {
        intentionallyNotImplemented()
    }

    override fun addBatch(p0: String?) {
        intentionallyNotImplemented()
    }

    override fun clearBatch() {
        intentionallyNotImplemented()
    }

    override fun executeBatch(): IntArray {
        intentionallyNotImplemented()
    }

    override fun getConnection(): Connection {
        intentionallyNotImplemented()
    }

    override fun getGeneratedKeys(): ResultSet {
        intentionallyNotImplemented()
    }

    override fun getResultSetHoldability(): Int {
        intentionallyNotImplemented()
    }

    override fun isClosed(): Boolean {
        intentionallyNotImplemented()
    }

    override fun setPoolable(p0: Boolean) {
        intentionallyNotImplemented()
    }

    override fun isPoolable(): Boolean {
        intentionallyNotImplemented()
    }

    override fun closeOnCompletion() {
        intentionallyNotImplemented()
    }

    override fun isCloseOnCompletion(): Boolean {
        intentionallyNotImplemented()
    }
}