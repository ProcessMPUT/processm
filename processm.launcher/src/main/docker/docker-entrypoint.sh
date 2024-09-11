#!/bin/sh

CONFIG_FILE=/processm/conf/config.properties
MSMTPRC_FILE=/processm/conf/msmtprc

if [ ! -f "$MSMTPRC_FILE" ] && [ -n "$MSMTPRC" ]
then
  echo "$MSMTPRC" >"$MSMTPRC_FILE"
fi

if [ ! -s "$PGDATA/PG_VERSION" ] && [ -z "$POSTGRES_PASSWORD" ]
then
  # + does not play well with URLs. Base64 could also produce =, but since we generate 12 random bytes, it does not.
  POSTGRES_PASSWORD=$(dd if=/dev/urandom count=1 bs=12|base64|sed 's/+/_/')
  export POSTGRES_PASSWORD
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
  chown root:processm "$CONFIG_FILE"
  chmod 640 "$CONFIG_FILE"
fi

#--auth-host to disable the default trust authentication in TCP/IP localhost connections
POSTGRES_INITDB_ARGS="--auth-host=scram-sha-256" /usr/local/bin/docker-entrypoint.sh postgres &

url=$(grep '^processm.core.persistence.connection.URL[^[:alnum:]]' <"$CONFIG_FILE" |tail -n 1|sed 's/^[^=]*=[[:space:]]*jdbc://')
URL="$url" exec gosu processm:processm sh docker-start-processm.sh