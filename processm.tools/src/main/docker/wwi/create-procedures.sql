use WideWorldImporters;
GO

DROP SCHEMA IF EXISTS ProcessM;
GO

CREATE SCHEMA ProcessM;
GO

DROP PROCEDURE IF EXISTS ProcessM.RemoveRandomLineFromCustomerOrder;
GO

CREATE PROCEDURE ProcessM.RemoveRandomLineFromCustomerOrder @OrderID int,
                                                            @OrderLineID int OUTPUT
    WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @OrderLineID = NULL;

    SELECT TOP (1) @OrderLineID = ol.OrderLineID
    FROM Sales.OrderLines ol
    WHERE ol.OrderID = @OrderID
      AND ol.PickingCompletedWhen is NULL
      AND (SELECT COUNT(*) FROM Sales.OrderLines ol2 WHERE ol2.OrderID = @OrderID) >= 2
    ORDER BY NEWID();

    IF @OrderLineID IS NOT NULL
        BEGIN
            DELETE FROM Sales.OrderLines WHERE OrderLineID = @OrderLineID;
        END;

END;

GO


DROP PROCEDURE IF EXISTS ProcessM.AddOrderLinesToCustomerOrder;
GO

CREATE PROCEDURE ProcessM.AddOrderLinesToCustomerOrder @CurrentDateTime datetime2(7),
                                                       @StartingWhen datetime,
                                                       @NumberOfOrderLines int,
                                                       @OrderID int
    WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;


    DECLARE @OrderLineCounter int = 0;
    DECLARE @CustomerID int;
    DECLARE @ExpectedDeliveryDate date = DATEADD(day, 1, @CurrentDateTime);
    DECLARE @StockItemID int;
    DECLARE @StockItemName nvarchar(100);
    DECLARE @UnitPackageID int;
    DECLARE @QuantityPerOuter int;
    DECLARE @Quantity int;
    DECLARE @CustomerPrice decimal(18, 2);
    DECLARE @TaxRate decimal(18, 3);

    -- No deliveries on weekends

    SET DATEFIRST 7;

    WHILE DATEPART(weekday, @ExpectedDeliveryDate) IN (1, 7)
        BEGIN
            SET @ExpectedDeliveryDate = DATEADD(day, 1, @ExpectedDeliveryDate);
        END;

    SELECT TOP (1) @CustomerID = o.CustomerID
    FROM Sales.Orders AS o
    WHERE o.OrderID = @OrderID;

    UPDATE Sales.Orders
    SET ExpectedDeliveryDate = @ExpectedDeliveryDate
    WHERE OrderID = @OrderID
      AND ExpectedDeliveryDate < @ExpectedDeliveryDate;

    SET @OrderLineCounter = 0;

    WHILE @OrderLineCounter < @NumberOfOrderLines
        BEGIN
            SELECT TOP (1) @StockItemID = si.StockItemID,
                           @StockItemName = si.StockItemName,
                           @UnitPackageID = si.UnitPackageID,
                           @QuantityPerOuter = si.QuantityPerOuter,
                           @TaxRate = si.TaxRate
            FROM Warehouse.StockItems AS si
            WHERE NOT EXISTS(SELECT 1
                             FROM Sales.OrderLines AS ol
                             WHERE ol.OrderID = @OrderID
                               AND ol.StockItemID = si.StockItemID)
            ORDER BY NEWID();

            IF @StockItemID IS NOT NULL
                BEGIN
                    SET @Quantity = @QuantityPerOuter * (1 + FLOOR(RAND() * 10));
                    SET @CustomerPrice = Website.CalculateCustomerPrice(@CustomerID, @StockItemID, @CurrentDateTime);

                    INSERT Sales.OrderLines
                    (OrderID, StockItemID, [Description], PackageTypeID, Quantity, UnitPrice,
                     TaxRate, PickedQuantity, PickingCompletedWhen, LastEditedBy, LastEditedWhen)
                    VALUES (@OrderID, @StockItemID, @StockItemName, @UnitPackageID, @Quantity, @CustomerPrice,
                            @TaxRate, 0, NULL, 1, @StartingWhen);

                    SET @OrderLineCounter += 1;
                END;
            ELSE
                BREAK;
        END;
END;

GO

DROP PROCEDURE IF EXISTS ProcessM.CreateCustomerOrder;
GO


CREATE PROCEDURE ProcessM.CreateCustomerOrder @CurrentDateTime datetime2(7),
                                              @StartingWhen datetime,
                                              @NumberOfOrderLines int,
                                              @OrderID int OUTPUT
    WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;


    DECLARE @CustomerID int;
    DECLARE @PrimaryContactPersonID int;
    DECLARE @SalespersonPersonID int;
    DECLARE @ExpectedDeliveryDate date = DATEADD(day, 1, @CurrentDateTime);
    DECLARE @OrderDateTime datetime = @StartingWhen;

    -- No deliveries on weekends

    SET DATEFIRST 7;

    WHILE DATEPART(weekday, @ExpectedDeliveryDate) IN (1, 7)
        BEGIN
            SET @ExpectedDeliveryDate = DATEADD(day, 1, @ExpectedDeliveryDate);
        END;

    -- Generate the required orders

    SET @OrderID = NEXT VALUE FOR Sequences.OrderID;

    SELECT TOP (1) @CustomerID = c.CustomerID,
                   @PrimaryContactPersonID = c.PrimaryContactPersonID
    FROM Sales.Customers AS c
    WHERE c.IsOnCreditHold = 0
    ORDER BY NEWID();

    SET @SalespersonPersonID = (SELECT TOP (1) PersonID
                                FROM [Application].People
                                WHERE IsSalesperson <> 0
                                ORDER BY NEWID());

    INSERT Sales.Orders
    (OrderID, CustomerID, SalespersonPersonID, PickedByPersonID, ContactPersonID, BackorderOrderID, OrderDate,
     ExpectedDeliveryDate, CustomerPurchaseOrderNumber, IsUndersupplyBackordered, Comments, DeliveryInstructions,
     InternalComments,
     PickingCompletedWhen, LastEditedBy, LastEditedWhen)
    VALUES (@OrderID, @CustomerID, @SalespersonPersonID, NULL, @PrimaryContactPersonID, NULL, @CurrentDateTime,
            @ExpectedDeliveryDate, CAST(CEILING(RAND() * 10000) + 10000 AS nvarchar(20)), 1, NULL, NULL, NULL,
            NULL, 1, @OrderDateTime);


    EXEC ProcessM.AddOrderLinesToCustomerOrder @CurrentDateTime, @StartingWhen, @NumberOfOrderLines, @OrderID;

END;

GO

DROP PROCEDURE IF EXISTS ProcessM.PickStockForCustomerOrder
GO

CREATE PROCEDURE ProcessM.PickStockForCustomerOrder @StartingWhen datetime,
                                                    @OrderID int,
                                                    @PickedSomething bit OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET XACT_ABORT ON;

    SET @PickedSomething = 0;

    DECLARE @UninvoicedOrders TABLE
                              (
                                  OrderID int PRIMARY KEY
                              );

    INSERT @UninvoicedOrders
    SELECT o.OrderID
    FROM Sales.Orders AS o
    WHERE NOT EXISTS(SELECT 1 FROM Sales.Invoices AS i WHERE i.OrderID = o.OrderID);

    DECLARE @StockAlreadyAllocated TABLE
                                   (
                                       StockItemID       int PRIMARY KEY,
                                       QuantityAllocated int
                                   );

    WITH StockAlreadyAllocated
             AS
             (
                 SELECT ol.StockItemID, SUM(ol.PickedQuantity) AS TotalPickedQuantity
                 FROM Sales.OrderLines AS ol
                          INNER JOIN @UninvoicedOrders AS uo
                                     ON ol.OrderID = uo.OrderID
                 WHERE ol.PickingCompletedWhen IS NULL
                 GROUP BY ol.StockItemID
             )
    INSERT
    @StockAlreadyAllocated
    (
    StockItemID
    ,
    QuantityAllocated
    )
    SELECT sa.StockItemID, sa.TotalPickedQuantity
    FROM StockAlreadyAllocated AS sa;

    DECLARE OrderLineList CURSOR FAST_FORWARD READ_ONLY
        FOR
        SELECT ol.OrderLineID, ol.StockItemID, ol.Quantity
        FROM Sales.OrderLines AS ol
        WHERE ol.PickingCompletedWhen IS NULL
          and ol.OrderID = @OrderID
        ORDER BY ol.OrderID, ol.OrderLineID;

    DECLARE @OrderLineID int;
    DECLARE @StockItemID int;
    DECLARE @Quantity int;
    DECLARE @AvailableStock int;
    -- TODO maybe pick person who picked something the last time in this Order?
    DECLARE @PickingPersonID int = (SELECT TOP (1) PersonID
                                    FROM [Application].People
                                    WHERE IsEmployee <> 0
                                    ORDER BY NEWID());

    OPEN OrderLineList;
    FETCH NEXT FROM OrderLineList INTO @OrderLineID, @StockItemID, @Quantity;

    WHILE @@FETCH_STATUS = 0 AND @PickedSomething = 0
        BEGIN
            -- work out available stock for this stock item (on hand less allocated)
            SET @AvailableStock = (SELECT QuantityOnHand
                                   FROM Warehouse.StockItemHoldings AS sih
                                   WHERE sih.StockItemID = @StockItemID);
            SET @AvailableStock -= COALESCE(
                    (SELECT QuantityAllocated FROM @StockAlreadyAllocated AS saa WHERE saa.StockItemID = @StockItemID),
                    0);

            IF @AvailableStock >= @Quantity
                BEGIN

                    MERGE @StockAlreadyAllocated AS saa
                    USING (VALUES (@StockItemID, @Quantity)) AS sa(StockItemID, Quantity)
                    ON saa.StockItemID = sa.StockItemID
                    WHEN MATCHED THEN
                        UPDATE SET saa.QuantityAllocated += sa.Quantity
                    WHEN NOT MATCHED THEN
                        INSERT (StockItemID, QuantityAllocated) VALUES (sa.StockItemID, sa.Quantity);

                    -- reserve the required stock
                    UPDATE Sales.OrderLines
                    SET PickedQuantity       = @Quantity,
                        PickingCompletedWhen = @StartingWhen,
                        LastEditedBy         = @PickingPersonID,
                        LastEditedWhen       = @StartingWhen
                    WHERE OrderLineID = @OrderLineID;

                    -- mark the order as ready to invoice (picking complete) if all lines picked
                    IF NOT EXISTS(SELECT 1
                                  FROM Sales.OrderLines AS ol
                                  WHERE ol.OrderID = @OrderID
                                    AND ol.PickingCompletedWhen IS NULL)
                        BEGIN
                            UPDATE Sales.Orders
                            SET PickingCompletedWhen = @StartingWhen,
                                PickedByPersonID     = @PickingPersonID,
                                LastEditedBy         = @PickingPersonID,
                                LastEditedWhen       = @StartingWhen
                            WHERE OrderID = @OrderID;
                        END;

                    SET @PickedSomething = 1;
                END;

            FETCH NEXT FROM OrderLineList INTO @OrderLineID, @StockItemID, @Quantity;
        END;

    CLOSE OrderLineList;
    DEALLOCATE OrderLineList;

END;
GO

DROP PROCEDURE IF EXISTS ProcessM.BackorderIfNecessary;
GO

CREATE PROCEDURE ProcessM.BackorderIfNecessary @StartingWhen datetime,
                                               @OrderID int,
                                               @BackorderOrderID int OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @PickingCompletedWhen datetime;
    DECLARE @BillToCustomerID int;
    DECLARE @InvoicingPersonID int = (SELECT TOP (1) PersonID
                                      FROM [Application].People
                                      WHERE IsEmployee <> 0
                                      ORDER BY NEWID());

    SET @BackorderOrderID = NULL;

    -- TODO IsConCreditHold does not seem to be used. Possibly we could leverage it somehow?

    DECLARE OrderList CURSOR FAST_FORWARD READ_ONLY
        FOR
        SELECT o.OrderID, o.PickingCompletedWhen, c.BillToCustomerID
        FROM Sales.Orders AS o
                 INNER JOIN Sales.Customers AS c
                            ON o.CustomerID = c.CustomerID
        WHERE NOT EXISTS(SELECT 1 FROM Sales.Invoices AS i WHERE i.OrderID = o.OrderID) -- not already invoiced
          AND o.OrderID = @OrderID
          AND c.IsOnCreditHold = 0                                                      -- and customer not on credit hold
          AND (o.PickingCompletedWhen IS NULL -- order not picked but customer happy
            AND o.IsUndersupplyBackordered <> 0 -- for part shipments and at least one
            AND EXISTS(SELECT 1
                       FROM Sales.OrderLines AS ol -- order line has been picked
                       WHERE ol.OrderID = o.OrderID
                         AND ol.PickingCompletedWhen IS NOT NULL));

    OPEN OrderList;
    FETCH NEXT FROM OrderList INTO @OrderID, @PickingCompletedWhen, @BillToCustomerID;

    WHILE @@FETCH_STATUS = 0
        BEGIN

            SET @BackorderOrderID = NEXT VALUE FOR Sequences.OrderID;
            SET @PickingCompletedWhen = @StartingWhen;

            -- create the backorder order
            INSERT Sales.Orders
            (OrderID, CustomerID, SalespersonPersonID, PickedByPersonID, ContactPersonID, BackorderOrderID,
             OrderDate, ExpectedDeliveryDate, CustomerPurchaseOrderNumber, IsUndersupplyBackordered,
             Comments, DeliveryInstructions, InternalComments, PickingCompletedWhen, LastEditedBy, LastEditedWhen)
            SELECT @BackorderOrderID,
                   o.CustomerID,
                   o.SalespersonPersonID,
                   NULL,
                   o.ContactPersonID,
                   NULL,
                   o.OrderDate,
                   o.ExpectedDeliveryDate,
                   o.CustomerPurchaseOrderNumber,
                   1,
                   o.Comments,
                   o.DeliveryInstructions,
                   o.InternalComments,
                   NULL,
                   @InvoicingPersonID,
                   @StartingWhen
            FROM Sales.Orders AS o
            WHERE o.OrderID = @OrderID;

            -- move the items that haven't been supplied to the new order
            UPDATE Sales.OrderLines
            SET OrderID        = @BackorderOrderID,
                LastEditedBy   = @InvoicingPersonID,
                LastEditedWhen = @StartingWhen
            WHERE OrderID = @OrderID
              AND PickingCompletedWhen IS NULL;

            -- flag the original order as backordered and picking completed
            UPDATE Sales.Orders
            SET BackorderOrderID     = @BackorderOrderID,
                PickingCompletedWhen = @PickingCompletedWhen,
                LastEditedBy         = @InvoicingPersonID,
                LastEditedWhen       = @StartingWhen
            WHERE OrderID = @OrderID;

            FETCH NEXT FROM OrderList INTO @OrderID, @PickingCompletedWhen, @BillToCustomerID;
        END;

    CLOSE OrderList;
    DEALLOCATE OrderList;

END;
GO

DROP PROCEDURE IF EXISTS ProcessM.InvoicePickedOrder;
GO

CREATE PROCEDURE ProcessM.InvoicePickedOrder @StartingWhen datetime,
                                             @OrderID int,
                                             @InvoiceID int OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @PickingCompletedWhen datetime;
    DECLARE @BillToCustomerID int;
    DECLARE @InvoicingPersonID int = (SELECT TOP (1) PersonID
                                      FROM [Application].People
                                      WHERE IsEmployee <> 0
                                      ORDER BY NEWID());
    DECLARE @PackedByPersonID int = (SELECT TOP (1) PersonID
                                     FROM [Application].People
                                     WHERE IsEmployee <> 0
                                     ORDER BY NEWID());
    DECLARE @TotalDryItems int;
    DECLARE @TotalChillerItems int;
    DECLARE @TransactionAmount decimal(18, 2);
    DECLARE @TaxAmount decimal(18, 2);
    DECLARE @ReturnedDeliveryData nvarchar(max);
    DECLARE @DeliveryEvent nvarchar(max);

    SET @InvoiceID = NULL;

    DECLARE OrderList CURSOR FAST_FORWARD READ_ONLY
        FOR
        SELECT o.OrderID, o.PickingCompletedWhen, c.BillToCustomerID
        FROM Sales.Orders AS o
                 INNER JOIN Sales.Customers AS c
                            ON o.CustomerID = c.CustomerID
        WHERE NOT EXISTS(SELECT 1 FROM Sales.Invoices AS i WHERE i.OrderID = o.OrderID) -- not already invoiced
          AND c.IsOnCreditHold = 0                                                      -- and customer not on credit hold
          AND o.OrderID = @OrderID
          AND o.PickingCompletedWhen IS NOT NULL;

    OPEN OrderList;
    FETCH NEXT FROM OrderList INTO @OrderID, @PickingCompletedWhen, @BillToCustomerID;

    WHILE @@FETCH_STATUS = 0
        BEGIN

            SELECT @TotalDryItems = SUM(CASE WHEN si.IsChillerStock <> 0 THEN 0 ELSE 1 END),
                   @TotalChillerItems = SUM(CASE WHEN si.IsChillerStock <> 0 THEN 1 ELSE 0 END)
            FROM Sales.OrderLines AS ol
                     INNER JOIN Warehouse.StockItems AS si
                                ON ol.StockItemID = si.StockItemID
            WHERE ol.OrderID = @OrderID;

            -- now invoice whatever is left on the order

            SET @InvoiceID = NEXT VALUE FOR Sequences.InvoiceID;

            SET @ReturnedDeliveryData = N'{"Events": []}';
            SET @DeliveryEvent = N'{ }';

            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Event', N'Ready for collection');
            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.EventTime', CONVERT(nvarchar(20), @StartingWhen, 126));
            SET @DeliveryEvent =
                    JSON_MODIFY(@DeliveryEvent, N'$.ConNote', N'EAN-125-' + CAST(@InvoiceID + 1050 AS nvarchar(20)));

            SET @ReturnedDeliveryData =
                    JSON_MODIFY(@ReturnedDeliveryData, N'append $.Events', JSON_QUERY(@DeliveryEvent));

            INSERT Sales.Invoices
            (InvoiceID, CustomerID, BillToCustomerID, OrderID, DeliveryMethodID, ContactPersonID, AccountsPersonID,
             SalespersonPersonID, PackedByPersonID, InvoiceDate, CustomerPurchaseOrderNumber,
             IsCreditNote, CreditNoteReason, Comments, DeliveryInstructions, InternalComments,
             TotalDryItems, TotalChillerItems,
             DeliveryRun, RunPosition, ReturnedDeliveryData, LastEditedBy, LastEditedWhen)
            SELECT @InvoiceID,
                   c.CustomerID,
                   @BillToCustomerID,
                   @OrderID,
                   c.DeliveryMethodID,
                   o.ContactPersonID,
                   btc.PrimaryContactPersonID,
                   o.SalespersonPersonID,
                   @PackedByPersonID,
                   @StartingWhen,
                   o.CustomerPurchaseOrderNumber,
                   0,
                   NULL,
                   NULL,
                   c.DeliveryAddressLine1 + N', ' + c.DeliveryAddressLine2,
                   NULL,
                   @TotalDryItems,
                   @TotalChillerItems,
                   c.DeliveryRun,
                   c.RunPosition,
                   @ReturnedDeliveryData,
                   @InvoicingPersonID,
                   @StartingWhen
            FROM Sales.Orders AS o
                     INNER JOIN Sales.Customers AS c
                                ON o.CustomerID = c.CustomerID
                     INNER JOIN Sales.Customers AS btc
                                ON btc.CustomerID = c.BillToCustomerID
            WHERE o.OrderID = @OrderID;

            INSERT Sales.InvoiceLines
            (InvoiceID, StockItemID, [Description], PackageTypeID,
             Quantity, UnitPrice, TaxRate, TaxAmount, LineProfit, ExtendedPrice,
             LastEditedBy, LastEditedWhen)
            SELECT @InvoiceID,
                   ol.StockItemID,
                   ol.[Description],
                   ol.PackageTypeID,
                   ol.PickedQuantity,
                   ol.UnitPrice,
                   ol.TaxRate,
                   ROUND(ol.PickedQuantity * ol.UnitPrice * ol.TaxRate / 100.0, 2),
                   ROUND(ol.PickedQuantity * (ol.UnitPrice - sih.LastCostPrice), 2),
                   ROUND(ol.PickedQuantity * ol.UnitPrice, 2)
                       + ROUND(ol.PickedQuantity * ol.UnitPrice * ol.TaxRate / 100.0, 2),
                   @InvoicingPersonID,
                   @StartingWhen
            FROM Sales.OrderLines AS ol
                     INNER JOIN Warehouse.StockItems AS si
                                ON ol.StockItemID = si.StockItemID
                     INNER JOIN Warehouse.StockItemHoldings AS sih
                                ON si.StockItemID = sih.StockItemID
            WHERE ol.OrderID = @OrderID
            ORDER BY ol.OrderLineID;

            INSERT Warehouse.StockItemTransactions
            (StockItemID, TransactionTypeID, CustomerID, InvoiceID, SupplierID, PurchaseOrderID,
             TransactionOccurredWhen, Quantity, LastEditedBy, LastEditedWhen)
            SELECT il.StockItemID,
                   (SELECT TransactionTypeID
                    FROM [Application].TransactionTypes
                    WHERE TransactionTypeName = N'Stock Issue'),
                   i.CustomerID,
                   i.InvoiceID,
                   NULL,
                   NULL,
                   @StartingWhen,
                   0 - il.Quantity,
                   @InvoicingPersonID,
                   @StartingWhen
            FROM Sales.InvoiceLines AS il
                     INNER JOIN Sales.Invoices AS i
                                ON il.InvoiceID = i.InvoiceID
            WHERE i.InvoiceID = @InvoiceID
            ORDER BY il.InvoiceLineID;

            WITH StockItemTotals
                     AS
                     (
                         SELECT il.StockItemID, SUM(il.Quantity) AS TotalQuantity
                         FROM Sales.InvoiceLines aS il
                         WHERE il.InvoiceID = @InvoiceID
                         GROUP BY il.StockItemID
                     )
            UPDATE sih
            SET sih.QuantityOnHand -= sit.TotalQuantity,
                sih.LastEditedBy   = @InvoicingPersonID,
                sih.LastEditedWhen = @StartingWhen
            FROM Warehouse.StockItemHoldings AS sih
                     INNER JOIN StockItemTotals AS sit
                                ON sih.StockItemID = sit.StockItemID;

            SELECT @TransactionAmount = SUM(il.ExtendedPrice),
                   @TaxAmount = SUM(il.TaxAmount)
            FROM Sales.InvoiceLines AS il
            WHERE il.InvoiceID = @InvoiceID;

            INSERT Sales.CustomerTransactions
            (CustomerID, TransactionTypeID, InvoiceID, PaymentMethodID,
             TransactionDate, AmountExcludingTax, TaxAmount, TransactionAmount,
             OutstandingBalance, FinalizationDate, LastEditedBy, LastEditedWhen)
            VALUES (@BillToCustomerID, (SELECT TransactionTypeID
                                        FROM [Application].TransactionTypes
                                        WHERE TransactionTypeName = N'Customer Invoice'),
                    @InvoiceID, NULL,
                    @StartingWhen, @TransactionAmount - @TaxAmount, @TaxAmount, @TransactionAmount,
                    @TransactionAmount, NULL, @InvoicingPersonID, @StartingWhen);
            FETCH NEXT FROM OrderList INTO @OrderID, @PickingCompletedWhen, @BillToCustomerID;
        END;

    CLOSE OrderList;
    DEALLOCATE OrderList;

END;
GO


DROP PROCEDURE IF EXISTS ProcessM.Deliver;
GO

CREATE PROCEDURE ProcessM.Deliver @StartingWhen datetime,
                                  @InvoiceID int,
                                  @Success bit,
                                  @IsDelivered bit OUTPUT
    WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @DeliveryDriverPersonID int = (SELECT TOP (1) PersonID
                                           FROM [Application].People
                                           WHERE IsEmployee <> 0
                                           ORDER BY NEWID());

    DECLARE @ReturnedDeliveryData nvarchar(max);
    DECLARE @CustomerName nvarchar(100);
    DECLARE @PrimaryContactFullName nvarchar(50);
    DECLARE @Latitude decimal(18, 7);
    DECLARE @Longitude decimal(18, 7);
    DECLARE @DeliveryAttemptWhen datetime2(7);
    DECLARE @Counter int = 0;
    DECLARE @DeliveryEvent nvarchar(max);

    SET @IsDelivered = 0;

    DECLARE InvoiceList CURSOR FAST_FORWARD READ_ONLY
        FOR
        SELECT i.InvoiceID, i.ReturnedDeliveryData, c.CustomerName, p.FullName, ct.[Location].Lat, ct.[Location].Long
        FROM Sales.Invoices AS i
                 INNER JOIN Sales.Customers AS c
                            ON i.CustomerID = c.CustomerID
                 INNER JOIN [Application].Cities AS ct
                            ON c.DeliveryCityID = ct.CityID
                 INNER JOIN [Application].People AS p
                            ON c.PrimaryContactPersonID = p.PersonID
        WHERE i.ConfirmedDeliveryTime IS NULL
          AND i.InvoiceID = @InvoiceID
          AND i.InvoiceDate < CAST(@StartingWhen AS date) -- Enabling same-day deliveries
        ORDER BY i.InvoiceID;

    OPEN InvoiceList;
    FETCH NEXT FROM InvoiceList INTO @InvoiceID, @ReturnedDeliveryData, @CustomerName, @PrimaryContactFullName, @Latitude, @Longitude;

    WHILE @@FETCH_STATUS = 0
        BEGIN
            SET @Counter += 1;
            SET @DeliveryAttemptWhen = DATEADD(minute, @Counter * 5, @StartingWhen);

            SET @DeliveryEvent = N'{ }';
            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Event', N'DeliveryAttempt');
            SET @DeliveryEvent =
                    JSON_MODIFY(@DeliveryEvent, N'$.EventTime', CONVERT(nvarchar(20), @DeliveryAttemptWhen, 126));
            SET @DeliveryEvent =
                    JSON_MODIFY(@DeliveryEvent, N'$.ConNote', N'EAN-125-' + CAST(@InvoiceID + 1050 AS nvarchar(20)));
            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.DriverID', @DeliveryDriverPersonID);
            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Latitude', @Latitude);
            SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Longitude', @Longitude);

            IF @Success = 0
                BEGIN
                    SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Comment', N'Receiver not present');
                END
            ELSE
                BEGIN
                    -- delivered
                    SET @DeliveryEvent = JSON_MODIFY(@DeliveryEvent, N'$.Status', N'Delivered');
                    SET @IsDelivered = 1;
                END;

            SET @ReturnedDeliveryData =
                    JSON_MODIFY(@ReturnedDeliveryData, N'append $.Events', JSON_QUERY(@DeliveryEvent));
            IF @IsDelivered = 1
                BEGIN
                    SET @ReturnedDeliveryData = JSON_MODIFY(@ReturnedDeliveryData, N'$.DeliveredWhen',
                                                            CONVERT(nvarchar(20), @DeliveryAttemptWhen, 126));
                    SET @ReturnedDeliveryData =
                            JSON_MODIFY(@ReturnedDeliveryData, N'$.ReceivedBy', @PrimaryContactFullName);
                END;

            UPDATE Sales.Invoices
            SET ReturnedDeliveryData = @ReturnedDeliveryData,
                LastEditedBy         = @DeliveryDriverPersonID,
                LastEditedWhen       = @StartingWhen
            WHERE InvoiceID = @InvoiceID;

            FETCH NEXT FROM InvoiceList INTO @InvoiceID, @ReturnedDeliveryData, @CustomerName, @PrimaryContactFullName, @Latitude, @Longitude;
        END;

    CLOSE InvoiceList;
    DEALLOCATE InvoiceList;
END;
GO

DROP PROCEDURE IF EXISTS ProcessM.ReceivePayment
GO

CREATE PROCEDURE ProcessM.ReceivePayment @StartingWhen datetime,
                                         @InvoiceID int
    WITH EXECUTE AS OWNER
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @StaffMemberPersonID int = (SELECT TOP (1) PersonID
                                        FROM [Application].People
                                        WHERE IsEmployee <> 0
                                        ORDER BY NEWID());

    DECLARE @TransactionsToReceive TABLE
                                   (
                                       CustomerTransactionID int,
                                       CustomerID            int,
                                       InvoiceID             int NULL,
                                       OutstandingBalance    decimal(18, 2)
                                   );

    INSERT @TransactionsToReceive
        (CustomerTransactionID, CustomerID, InvoiceID, OutstandingBalance)
    SELECT CustomerTransactionID, CustomerID, InvoiceID, OutstandingBalance
    FROM Sales.CustomerTransactions
    WHERE IsFinalized = 0
      AND InvoiceID = @InvoiceID;


    UPDATE Sales.CustomerTransactions
    SET OutstandingBalance = 0,
        FinalizationDate   = @StartingWhen,
        LastEditedBy       = @StaffMemberPersonID,
        LastEditedWhen     = @StartingWhen
    WHERE CustomerTransactionID IN (SELECT CustomerTransactionID FROM @TransactionsToReceive);

    INSERT Sales.CustomerTransactions
    (CustomerID, TransactionTypeID, InvoiceID, PaymentMethodID, TransactionDate,
     AmountExcludingTax, TaxAmount, TransactionAmount, OutstandingBalance,
     FinalizationDate, LastEditedBy, LastEditedWhen)
    SELECT ttr.CustomerID,
           (SELECT TransactionTypeID
            FROM [Application].TransactionTypes
            WHERE TransactionTypeName = N'Customer Payment Received'),
           NULL,
           (SELECT PaymentMethodID FROM [Application].PaymentMethods WHERE PaymentMethodName = N'EFT'),
           CAST(@StartingWhen AS date),
           0,
           0,
           0 - SUM(ttr.OutstandingBalance),
           0,
           CAST(@StartingWhen AS date),
           @StaffMemberPersonID,
           @StartingWhen
    FROM @TransactionsToReceive AS ttr
    GROUP BY ttr.CustomerID;

END;
GO

DROP PROCEDURE IF EXISTS ProcessM.PlacePurchaseOrders
GO

CREATE PROCEDURE ProcessM.PlacePurchaseOrders @StartingWhen datetime
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @ContactPersonID int = (SELECT TOP (1) PersonID
                                    FROM [Application].People
                                    WHERE IsEmployee <> 0
                                    ORDER BY NEWID());

    DECLARE @Orders TABLE
                    (
                        SupplierID        int,
                        PurchaseOrderID   int          NULL,
                        DeliveryMethodID  int,
                        ContactPersonID   int,
                        SupplierReference nvarchar(20) NULL
                    );

    DECLARE @OrderLines TABLE
                        (
                            StockItemID        int,
                            [Description]      nvarchar(100),
                            SupplierID         int,
                            QuantityOfOuters   int,
                            LeadTimeDays       int,
                            OuterPackageID     int,
                            LastOuterCostPrice decimal(18, 2)
                        );

    WITH StockItemsToCheck
             AS
             (
                 SELECT si.StockItemID,
                        si.StockItemName                                     AS [Description],
                        si.SupplierID,
                        sih.TargetStockLevel,
                        sih.ReorderLevel,
                        si.QuantityPerOuter,
                        si.LeadTimeDays,
                        si.OuterPackageID,
                        sih.QuantityOnHand,
                        sih.LastCostPrice,
                        COALESCE((SELECT SUM(ol.Quantity)
                                  FROM Sales.OrderLines AS ol
                                  WHERE ol.StockItemID = si.StockItemID
                                    AND ol.PickingCompletedWhen IS NULL), 0) AS StockNeededForOrders,
                        COALESCE((SELECT si.QuantityPerOuter * SUM(pol.OrderedOuters - pol.ReceivedOuters)
                                  FROM Purchasing.PurchaseOrderLines AS pol
                                  WHERE pol.StockItemID = si.StockItemID
                                    AND pol.IsOrderLineFinalized = 0), 0)    AS StockOnOrder
                 FROM Warehouse.StockItems AS si
                          INNER JOIN Warehouse.StockItemHoldings AS sih
                                     ON si.StockItemID = sih.StockItemID
             ),
         StockItemsToOrder
             AS
             (
                 SELECT sitc.StockItemID,
                        sitc.[Description],
                        sitc.SupplierID,
                        (sitc.QuantityOnHand + sitc.StockOnOrder - sitc.StockNeededForOrders) AS EffectiveStockLevel,
                        sitc.TargetStockLevel,
                        sitc.QuantityPerOuter,
                        sitc.LeadTimeDays,
                        sitc.OuterPackageID,
                        sitc.LastCostPrice
                 FROM StockItemsToCheck AS sitc
                 WHERE (sitc.QuantityOnHand + sitc.StockOnOrder - sitc.StockNeededForOrders) < sitc.ReorderLevel
                   AND sitc.QuantityPerOuter <> 0
             )
    INSERT
    @OrderLines
    (
    StockItemID
    ,
    [Description]
    ,
    SupplierID
    ,
    QuantityOfOuters
    ,
    LeadTimeDays
    ,
    OuterPackageID
    ,
    LastOuterCostPrice
    )
    SELECT sito.StockItemID,
           sito.[Description],
           sito.SupplierID,
           CEILING((sito.TargetStockLevel - sito.EffectiveStockLevel) / sito.QuantityPerOuter) AS OutersRequired,
           sito.LeadTimeDays,
           sito.OuterPackageID,
           ROUND(sito.LastCostPrice * sito.QuantityPerOuter, 2)                                AS LastOuterCostPrice
    FROM StockItemsToOrder AS sito;

    INSERT @Orders (SupplierID, PurchaseOrderID, DeliveryMethodID, ContactPersonID, SupplierReference)

    SELECT s.SupplierID,
           NEXT VALUE FOR Sequences.PurchaseOrderID,
           s.DeliveryMethodID,
           (SELECT TOP (1) PersonID FROM [Application].People WHERE IsEmployee <> 0),
           s.SupplierReference
    FROM Purchasing.Suppliers AS s
    WHERE s.SupplierID IN (SELECT SupplierID FROM @OrderLines);

    INSERT Purchasing.PurchaseOrders
    (PurchaseOrderID, SupplierID, OrderDate, DeliveryMethodID, ContactPersonID,
     ExpectedDeliveryDate, SupplierReference, IsOrderFinalized, Comments,
     InternalComments, LastEditedBy, LastEditedWhen)
    SELECT o.PurchaseOrderID,
           o.SupplierID,
           CAST(@StartingWhen AS date),
           o.DeliveryMethodID,
           o.ContactPersonID,
           DATEADD(day, (SELECT MAX(LeadTimeDays) FROM @OrderLines), CAST(@StartingWhen AS date)),
           o.SupplierReference,
           0,
           NULL,
           NULL,
           1,
           @StartingWhen
    FROM @Orders AS o;

    INSERT Purchasing.PurchaseOrderLines
    (PurchaseOrderID, StockItemID, OrderedOuters, [Description],
     ReceivedOuters, PackageTypeID, ExpectedUnitPricePerOuter, LastReceiptDate,
     IsOrderLineFinalized, LastEditedBy, LastEditedWhen)
    SELECT o.PurchaseOrderID,
           ol.StockItemID,
           ol.QuantityOfOuters,
           ol.[Description],
           0,
           ol.OuterPackageID,
           ol.LastOuterCostPrice,
           NULL,
           0,
           @ContactPersonID,
           @StartingWhen
    FROM @OrderLines AS ol
             INNER JOIN @Orders AS o
                        ON ol.SupplierID = o.SupplierID;
    SELECT o.PurchaseOrderID FROM @Orders as o; -- returned to the client
END;
GO

DROP PROCEDURE IF EXISTS ProcessM.ReceivePurchaseOrder
GO

CREATE PROCEDURE ProcessM.ReceivePurchaseOrder @StartingWhen datetime,
                                               @PurchaseOrderID int,
                                               @DeliveryCompleted bit OUTPUT
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    SET @DeliveryCompleted = 0;

    DECLARE @StaffMemberPersonID int = (SELECT TOP (1) PersonID
                                        FROM [Application].People
                                        WHERE IsEmployee <> 0
                                        ORDER BY NEWID());
    DECLARE @SupplierID int;
    DECLARE @TotalExcludingTax decimal(18, 2);
    DECLARE @TotalIncludingTax decimal(18, 2);

    DECLARE PurchaseOrderList CURSOR FAST_FORWARD READ_ONLY
        FOR
        SELECT PurchaseOrderID, SupplierID
        FROM Purchasing.PurchaseOrders AS po
        WHERE po.IsOrderFinalized = 0
          AND po.ExpectedDeliveryDate <= @StartingWhen -- Interestingly enought the sign is in the other direction in the original procedure in WWI
          AND po.PurchaseOrderID = @PurchaseOrderID;

    OPEN PurchaseOrderList;
    FETCH NEXT FROM PurchaseOrderList INTO @PurchaseOrderID, @SupplierID;

    WHILE @@FETCH_STATUS = 0
        BEGIN

            UPDATE Purchasing.PurchaseOrderLines
            SET ReceivedOuters       = OrderedOuters,
                IsOrderLineFinalized = 1,
                LastReceiptDate      = CAST(@StartingWhen as date),
                LastEditedBy         = @StaffMemberPersonID,
                LastEditedWhen       = @StartingWhen
            WHERE PurchaseOrderID = @PurchaseOrderID;

            UPDATE sih
            SET sih.QuantityOnHand += pol.ReceivedOuters * si.QuantityPerOuter,
                sih.LastEditedBy   = @StaffMemberPersonID,
                sih.LastEditedWhen = @StartingWhen
            FROM Warehouse.StockItemHoldings AS sih
                     INNER JOIN Purchasing.PurchaseOrderLines AS pol
                                ON sih.StockItemID = pol.StockItemID
                     INNER JOIN Warehouse.StockItems AS si
                                ON sih.StockItemID = si.StockItemID;

            INSERT Warehouse.StockItemTransactions
            (StockItemID, TransactionTypeID, CustomerID, InvoiceID, SupplierID, PurchaseOrderID,
             TransactionOccurredWhen, Quantity, LastEditedBy, LastEditedWhen)
            SELECT pol.StockItemID,
                   (SELECT TransactionTypeID
                    FROM [Application].TransactionTypes
                    WHERE TransactionTypeName = N'Stock Receipt'),
                   NULL,
                   NULL,
                   @SupplierID,
                   pol.PurchaseOrderID,
                   @StartingWhen,
                   pol.ReceivedOuters * si.QuantityPerOuter,
                   @StaffMemberPersonID,
                   @StartingWhen
            FROM Purchasing.PurchaseOrderLines AS pol
                     INNER JOIN Warehouse.StockItems AS si
                                ON pol.StockItemID = si.StockItemID
            WHERE pol.PurchaseOrderID = @PurchaseOrderID;

            UPDATE Purchasing.PurchaseOrders
            SET IsOrderFinalized = 1,
                LastEditedBy     = @StaffMemberPersonID,
                LastEditedWhen   = @StartingWhen
            WHERE PurchaseOrderID = @PurchaseOrderID;

            SELECT @TotalExcludingTax = SUM(ROUND(pol.OrderedOuters * pol.ExpectedUnitPricePerOuter, 2)),
                   @TotalIncludingTax = SUM(ROUND(pol.OrderedOuters * pol.ExpectedUnitPricePerOuter, 2))
                       + SUM(ROUND(pol.OrderedOuters * pol.ExpectedUnitPricePerOuter * si.TaxRate / 100.0, 2))
            FROM Purchasing.PurchaseOrderLines AS pol
                     INNER JOIN Warehouse.StockItems AS si
                                ON pol.StockItemID = si.StockItemID
            WHERE pol.PurchaseOrderID = @PurchaseOrderID;

            INSERT Purchasing.SupplierTransactions
            (SupplierID, TransactionTypeID, PurchaseOrderID, PaymentMethodID,
             SupplierInvoiceNumber, TransactionDate, AmountExcludingTax,
             TaxAmount, TransactionAmount, OutstandingBalance,
             FinalizationDate, LastEditedBy, LastEditedWhen)
            VALUES (@SupplierID, (SELECT TransactionTypeID
                                  FROM [Application].TransactionTypes
                                  WHERE TransactionTypeName = N'Supplier Invoice'),
                    @PurchaseOrderID,
                    (SELECT PaymentMethodID FROM [Application].PaymentMethods WHERE PaymentMethodName = N'EFT'),
                    CAST(CEILING(RAND() * 10000) AS nvarchar(20)), CAST(@StartingWhen AS date), @TotalExcludingTax,
                    @TotalIncludingTax - @TotalExcludingTax, @TotalIncludingTax, @TotalIncludingTax,
                    NULL, @StaffMemberPersonID, @StartingWhen);

            SET @DeliveryCompleted = 1;

            FETCH NEXT FROM PurchaseOrderList INTO @PurchaseOrderID, @SupplierID;
        END;

    CLOSE PurchaseOrderList;
    DEALLOCATE PurchaseOrderList;
END;
GO

DROP PROCEDURE IF EXISTS ProcessM.PaySupplier;
GO

CREATE PROCEDURE ProcessM.PaySupplier @StartingWhen datetime,
                                      @PurchaseOrderID int
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;

    DECLARE @StaffMemberPersonID int = (SELECT TOP (1) PersonID
                                        FROM [Application].People
                                        WHERE IsEmployee <> 0
                                        ORDER BY NEWID());

    DECLARE @TransactionsToPay TABLE
                               (
                                   SupplierTransactionID int,
                                   SupplierID            int,
                                   PurchaseOrderID       int          NULL,
                                   SupplierInvoiceNumber nvarchar(20) NULL,
                                   OutstandingBalance    decimal(18, 2)
                               );

    INSERT @TransactionsToPay
    (SupplierTransactionID, SupplierID, PurchaseOrderID, SupplierInvoiceNumber, OutstandingBalance)
    SELECT SupplierTransactionID, SupplierID, PurchaseOrderID, SupplierInvoiceNumber, OutstandingBalance
    FROM Purchasing.SupplierTransactions
    WHERE IsFinalized = 0
      and PurchaseOrderID = @PurchaseOrderID;

    UPDATE Purchasing.SupplierTransactions
    SET OutstandingBalance = 0,
        FinalizationDate   = @StartingWhen,
        LastEditedBy       = @StaffMemberPersonID,
        LastEditedWhen     = @StartingWhen
    WHERE SupplierTransactionID IN (SELECT SupplierTransactionID FROM @TransactionsToPay);

    INSERT Purchasing.SupplierTransactions
    (SupplierID, TransactionTypeID, PurchaseOrderID, PaymentMethodID,
     SupplierInvoiceNumber, TransactionDate, AmountExcludingTax, TaxAmount, TransactionAmount,
     OutstandingBalance, FinalizationDate, LastEditedBy, LastEditedWhen)
    SELECT ttp.SupplierID,
           (SELECT TransactionTypeID
            FROM [Application].TransactionTypes
            WHERE TransactionTypeName = N'Supplier Payment Issued'),
           NULL,
           (SELECT PaymentMethodID FROM [Application].PaymentMethods WHERE PaymentMethodName = N'EFT'),
           NULL,
           CAST(@StartingWhen AS date),
           0,
           0,
           0 - SUM(ttp.OutstandingBalance),
           0,
           CAST(@StartingWhen AS date),
           @StaffMemberPersonID,
           @StartingWhen
    FROM @TransactionsToPay AS ttp
    GROUP BY ttp.SupplierID;

END;
GO