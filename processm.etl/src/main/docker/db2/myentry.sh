#!/bin/bash

if [ -e /database.tar.xz ]
then
	mkdir /database
	cd /database
	tar xvf /database.tar.xz
fi

/var/db2_setup/lib/setup_db2_instance.sh
