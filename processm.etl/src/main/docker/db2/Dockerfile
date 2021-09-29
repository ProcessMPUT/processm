# not using 11.5.6, because Db2Container expects the DB to operate on the port 50000 and IBM changed the defaults in 11.5.6 - see https://www.ibm.com/support/pages/db2-db2-server-tcpip-port-numbers
FROM ibmcom/db2:11.5.5.1
COPY myentry.sh /
COPY packdb.sh /
ENTRYPOINT /bin/bash /myentry.sh
