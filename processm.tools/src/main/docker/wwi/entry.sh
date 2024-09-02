#!/bin/bash

export MSSQL_SA_PASSWORD=${SA_PASSWORD:-A_Str0ng_Required_Password}
export SQLCMDPASSWORD=$MSSQL_SA_PASSWORD

SQLCMD="/opt/mssql-tools/bin/sqlcmd -U SA -I"

/opt/mssql/bin/sqlservr --accept-eula --reset-sa-password &

for i in $(seq 1 100)
do
   $SQLCMD </dev/null && break
   sleep 1
done

exec java -D'processm.tools.generator.dbURL=jdbc:sqlserver://localhost:1433;database=WideWorldImporters;trustServerCertificate=true' -D'processm.tools.generator.dbUser=SA' -D"processm.tools.generator.dbPassword=$MSSQL_SA_PASSWORD" -jar "/processm/processm.tools-${PROCESSM_VERSION}-jar-with-dependencies.jar"
