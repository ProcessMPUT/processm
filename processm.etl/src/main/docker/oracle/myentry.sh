#!/bin/bash

if [ -e /database.tar.lzma ]
then
	cd /opt/oracle
	xz --format=lzma --decompress --stdout /database.tar.lzma | tar xvf -
fi

cd /
exec /opt/oracle/runOracle.sh
