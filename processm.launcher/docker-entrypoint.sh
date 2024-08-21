#!/bin/sh

CONFIG_FILE=/processm/conf/config.properties
MSMTPRC_FILE=/processm/conf/msmtprc
SLEEP=1
REPETITIONS=10

if [ ! -f "$MSMTPRC_FILE" ] && [ -n "$MSMTPRC" ]
then
  echo "$MSMTPRC" >"$MSMTPRC_FILE"
fi

if [ -n "$POSTGRES_PASSWORD" ]
then
  # := is used to set the varabiles if they are not set. This ensures that the DB initialization script will not use different defaults
  export POSTGRES_DB=${POSTGRES_DB:=processm}
  export POSTGRES_USER=${POSTGRES_USER:=postgres}
  url="jdbc:postgresql://localhost:5432/$POSTGRES_DB?user=$POSTGRES_USER&password=$POSTGRES_PASSWORD"
  tmp=$(mktemp)
  (sed 's/^processm.core.persistence.connection.URL[^[:alnum:]].*$/#&/gi' <"$CONFIG_FILE"; echo "processm.core.persistence.connection.URL=$url") >"$tmp"
  mv "$tmp" "$CONFIG_FILE"
fi

url=$(grep '^processm.core.persistence.connection.URL[^[:alnum:]]' <"$CONFIG_FILE" |tail -n 1|sed 's/^[^=]*=[[:space:]]*jdbc://')

/usr/local/bin/docker-entrypoint.sh postgres &

for i in $(seq 1 $REPETITIONS)
do
  if psql "$url" </dev/null
  then
    exec java -Xmx8G -jar "launcher-$PROCESSM_VERSION.jar"
  else
    sleep $SLEEP
  fi
done

echo "The database is not available at $url. Terminating."
exit 1