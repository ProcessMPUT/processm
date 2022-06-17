#!/bin/bash

if [ -e /tmp/test-databases/$DB_FILE ]
then
	mkdir /database
	cd /database
	tar xvf /tmp/test-databases/$DB_FILE
fi

/var/db2_setup/lib/setup_db2_instance.sh
