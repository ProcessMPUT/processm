#!/bin/sh
# Make sure any modifications are compatible with busybox, as the Docker image uses it.
dir=$(dirname "$0")
cd "$dir"
java -Xmx8G -jar launcher-0.7.0.jar
