#!/bin/sh

SLEEP=1
REPETITIONS=120

if [ "$(id -u)" -eq 0 ]
then
  echo "ProcessM should not be started as root"
  exit 1
fi

if grep -qE '^[[:digit:]]+$' /sys/fs/cgroup/memory.max
then
  # the value is in bytes, hence we divide by 1024
  mem=$(cat /sys/fs/cgroup/memory.max)
  mem=$(($mem/1024))
else
  # the value is in kilobytes
  mem=$(sed -E 's/^.* ([[:digit:]]*) .*$/\1/;q' </proc/meminfo)
fi
mem=$(($mem/2))

echo "ProcessM will use $mem kB of memory for the heap"

for i in $(seq 1 $REPETITIONS)
do
  if psql "$URL" </dev/null
  then
    exec java -Xmx"$mem"k -jar launcher-$PROCESSM_VERSION.jar
  else
    sleep $SLEEP
  fi
done

echo "The database is not available at $URL. Terminating."
exit 1