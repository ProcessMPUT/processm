# ProcessM

ProcessM is an artificial intelligence tool supporting business process management. ProcessM seamlessly integrates with
variety of data sources such as database systems and ERP tools using fully-configurable ETL processes. ProcessM models
he business process in online mode based on the incoming in real-time events from the data sources system, verifies the
conformance of the process with the model, classifies detected deviations from the model into errors and concept drift,
reports deviations together with a root cause analysis and calculates KPIs for performance analysis and bottleneck
identification. ProcessM is a web-service that works in a continuous mode and does the above tasks unattended.
Detected deviations in the operation of the process are immediately reported to the user. The web-service of ProcessM
is compatible with Windows, Linux and MacOS, and the client of ProcessM works in a web-browser running on virtually
any operating system.

## Getting started

### Docker image

For Docker users, download and run ProcessM by simply executing the following command:

```bash
docker run -p 80:2080 -p 443:2443 -d --name processm processm/processm-server-full:latest
```

### Downloads

TODO: #282 github releases link

## Configuration

TODO

## Build & deployment from sources

Execute the following instructions in the project's main directory.

### Creating deployment package

To produce a deployment package run

```shell
mvn package
```
or
```shell
mvn package -DskipTests=true -P production
```

The above produces a deployment package `./processm.launcher/target/processm-<version>.tar.xz`. To run, extract archive
and execute command `./processm.sh` (linux/macOS) or `processm.bat` (Windows).

The above command also produces the docker image `processm:<version>`. Docker Desktop is required to be installed.

### Exporting the image to file and uploading it

Instead of pushing the image to a remote repository, transfer it directly to the server.

```shell
docker save -o processm-docker-image.tar processm:<version> && scp ./processm-docker-image.tar <server-address>:~
```

### Applying deployment package

On the server:

#### Configuring database access

Make sure the environment variable `PROCESSM_CORE_PERSISTENCE_CONNECTION_URL` is set with database connection string.

#### Loading the image into Docker

Instead of pulling the image from a remote repository we are importing it from the uploaded file.

```shell
docker load -i ./processm-docker-image.tar
```

#### Run 

```shell
docker run -d --name processm -p 2080:2080 -p 2443:2443 --env PROCESSM_CORE_PERSISTENCE_CONNECTION_URL processm
```
