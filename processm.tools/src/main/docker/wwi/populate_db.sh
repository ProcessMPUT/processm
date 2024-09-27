#!/bin/bash

export MSSQL_SA_PASSWORD=${SA_PASSWORD:-A_Str0ng_Required_Password}
export SQLCMDPASSWORD=$MSSQL_SA_PASSWORD

SQLCMD="/opt/mssql-tools18/bin/sqlcmd -U SA -I -No"

/opt/mssql/bin/sqlservr --accept-eula --reset-sa-password &
MSSQL_PID=$!

for i in `seq 1 10`
do
   $SQLCMD </dev/null && break
   sleep 10
done

$SQLCMD -i /processm/load-wwi.sql || exit 1
$SQLCMD -i /processm/create-procedures.sql || exit 1

kill $MSSQL_PID
wait
