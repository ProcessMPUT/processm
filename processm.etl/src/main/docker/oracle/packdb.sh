#!/bin/bash

/etc/init.d/oracle-xe-18c stop
cd /opt/oracle
echo "Be patient, compressing the DB takes time."
tar c oradata | xz --format=lzma --compress -9 > /tmp/test-databases/oracle/database.tar.lzma
