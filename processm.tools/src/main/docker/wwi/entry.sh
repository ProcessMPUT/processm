#!/bin/bash

export MSSQL_SA_PASSWORD=${SA_PASSWORD:-A_Str0ng_Required_Password}
export SQLCMDPASSWORD=$MSSQL_SA_PASSWORD

SQLCMD="/opt/mssql-tools/bin/sqlcmd -U SA -I"

/opt/mssql/bin/sqlservr --accept-eula --reset-sa-password &
MSSQL_PID=$!

for i in `seq 1 10`
do
   $SQLCMD </dev/null && break
   sleep 10
done

$SQLCMD -i /processm/load-wwi.sql || exit 1
$SQLCMD -i /processm/create-procedures.sql || exit 1

exec java -D'processm.tools.generator.dbURL=jdbc:sqlserver://localhost:1433;database=WideWorldImporters' -D'processm.tools.generator.dbUser=SA' -D"processm.tools.generator.dbPassword=$MSSQL_SA_PASSWORD" -jar /processm/processm.tools-0.1-SNAPSHOT-jar-with-dependencies.jar