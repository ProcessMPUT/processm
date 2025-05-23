package processm.tools.generator

import processm.tools.helpers.ConnectionPool
import processm.tools.helpers.LazyConnectionPool
import java.sql.Connection
import java.sql.Timestamp
import java.sql.Types

class WWIConnectionPool(basePool: ConnectionPool) : ConnectionPool by basePool {
    constructor(maxSize: Int, createConnection: () -> Connection) :
            this(LazyConnectionPool(maxSize, createConnection))

    val pickStockForCustomerOrder =
        wrapStoredProcedure3<Timestamp, Int, Boolean>(Types.BIT, "ProcessM.PickStockForCustomerOrder")
    val backorderIfNecessary =
        wrapStoredProcedure3<Timestamp, Int, Int?>(Types.INTEGER, "ProcessM.BackorderIfNecessary")
    val invoicePickedOrder =
        wrapStoredProcedure3<Timestamp, Int, Int?>(Types.INTEGER, "ProcessM.InvoicePickedOrder")
    val deliver = wrapStoredProcedure4<Timestamp, Int, Boolean, Boolean>(Types.BOOLEAN, "ProcessM.Deliver")
    val receivePayment = wrapStoredProcedure3<Timestamp, Int, Unit>(Types.NULL, "ProcessM.ReceivePayment")
    val createCustomerOrder =
        wrapStoredProcedure4<Timestamp, Timestamp, Int, Int>(Types.INTEGER, "ProcessM.CreateCustomerOrder")
    val placePurchaseOrders = wrapStoredProcedure1RS1<Timestamp, Int>("ProcessM.PlacePurchaseOrders")
    val receivePurchaseOrder =
        wrapStoredProcedure3<Timestamp, Int, Boolean>(Types.BOOLEAN, "ProcessM.ReceivePurchaseOrder")
    val paySupplier = wrapStoredProcedure3<Timestamp, Int, Unit>(Types.NULL, "ProcessM.PaySupplier")
    val removeRandomLineFromCustomerOrder =
        wrapStoredProcedure2<Int, Int?>(Types.INTEGER, "ProcessM.RemoveRandomLineFromCustomerOrder")
    val addOrderLinesToCustomerOrder =
        wrapStoredProcedure5<Timestamp, Timestamp, Int, Int, Unit>(Types.NULL, "ProcessM.AddOrderLinesToCustomerOrder")
}