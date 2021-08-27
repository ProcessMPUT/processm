#!/bin/bash

if [ -e /database.tgz ]
then
	mkdir /database
	cd /database
	tar xvf /database.tgz
fi

/var/db2_setup/lib/setup_db2_instance.sh
