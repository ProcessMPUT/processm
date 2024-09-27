restore database WideWorldImporters from disk ='/processm/WideWorldImporters-Full.bak'
    with move 'WWI_Primary' to '/var/opt/mssql/data/WideWorldImporters.mdf',
        move 'WWI_UserData' to '/var/opt/mssql/data/WideWorldImporters_UserData.ndf',
        move 'WWI_Log' to '/var/opt/mssql/data/WideWorldImporters.ldf',
        move 'WWI_InMemory_Data_1' to '/var/opt/mssql/data/WideWorldImporters_InMemory_Data_1';
GO

use WideWorldImporters;
GO

TRUNCATE TABLE Sales.InvoiceLines;
TRUNCATE TABLE Sales.OrderLines;
TRUNCATE TABLE Sales.CustomerTransactions;
TRUNCATE TABLE Warehouse.StockItemTransactions;
DELETE FROM Sales.Invoices;
DELETE FROM Sales.Orders;
TRUNCATE TABLE Purchasing.SupplierTransactions;
TRUNCATE TABLE Purchasing.PurchaseOrderLines;
DELETE FROM Purchasing.PurchaseOrders;
GO