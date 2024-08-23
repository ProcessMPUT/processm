#!/bin/sh
PROCESSM_VERSION=${PROCESSM_VERSION:=0.7.0}
# Make sure any modifications are compatible with busybox, as the Docker image uses it.
dir=$(dirname "$0")
cd "$dir"
java -Xmx8G -jar "launcher-$PROCESSM_VERSION.jar"
