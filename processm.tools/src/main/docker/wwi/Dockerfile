FROM mcr.microsoft.com/mssql/server:2019-CU12-ubuntu-20.04
ARG PROCESSM_VERSION=0.7.0
ENV PROCESSM_VERSION=$PROCESSM_VERSION
USER root:root
RUN apt-get update && apt-get install -yyy openjdk-17-jre-headless && apt-get clean && rm -rf /var/lib/apt/lists/* ; \
     /opt/mssql/bin/mssql-conf set sqlagent.enabled true
USER mssql
RUN --mount=type=bind,source=src/main/docker/wwi/,target=/processm/ sh /processm/populate_db.sh
COPY src/main/docker/wwi/entry.sh target/processm.tools-${PROCESSM_VERSION}-jar-with-dependencies.jar /processm/
ENTRYPOINT ["/bin/bash", "/processm/entry.sh"]
