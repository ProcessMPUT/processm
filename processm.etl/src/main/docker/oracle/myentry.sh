#!/bin/bash

if [ -e /tmp/test-databases/oracle/database.tar.lzma ]
then
	cd /opt/oracle
	xz --format=lzma --decompress --stdout /tmp/test-databases/oracle/database.tar.lzma | tar xvf -
fi

cd /
exec /opt/oracle/runOracle.sh
