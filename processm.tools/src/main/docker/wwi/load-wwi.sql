restore database WideWorldImporters from disk ='/processm/WideWorldImporters-Full.bak'
    with move 'WWI_Primary' to '/tmp/wwi/WideWorldImporters.mdf',
        move 'WWI_UserData' to '/tmp/wwi/WideWorldImporters_UserData.ndf',
        move 'WWI_Log' to '/tmp/wwi/WideWorldImporters.ldf',
        move 'WWI_InMemory_Data_1' to '/tmp/wwi/WideWorldImporters_InMemory_Data_1';
GO