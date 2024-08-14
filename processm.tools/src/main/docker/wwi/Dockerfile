FROM mcr.microsoft.com/mssql/server:2019-CU12-ubuntu-20.04
EXPOSE 1433/tcp
USER root:root
RUN apt-get update && apt-get install -yyy openjdk-17-jre-headless && apt-get clean && rm -rf /var/lib/apt/lists/*
USER mssql
COPY src/main/docker/wwi/entry.sh src/main/docker/wwi/create-procedures.sql src/main/docker/wwi/load-wwi.sql src/main/docker/wwi/WideWorldImporters-Full.bak target/processm.tools-${project.version}-jar-with-dependencies.jar /processm/
ENTRYPOINT ["/bin/bash", "/processm/entry.sh"]
