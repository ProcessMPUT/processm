#!/bin/sh

SLEEP=1
REPETITIONS=10

if [ "$(id -u)" -eq 0 ]
then
  echo "ProcessM should not be started as root"
  exit 1
fi

for i in $(seq 1 $REPETITIONS)
do
  if psql "$URL" </dev/null
  then
    exec java -Xmx8G -jar "launcher-$PROCESSM_VERSION.jar"
  else
    sleep $SLEEP
  fi
done

echo "The database is not available at $URL. Terminating."
exit 1