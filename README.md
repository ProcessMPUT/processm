# processm

## Deployment with Docker

### Preparing deployment package

Execute the following instructions in the project's main directory.

#### Creating deployment package

```shell
mvn package
```
or
```shell
mvn package -DskipTests=true -P production
```

The above produces Uber JAR (containing code from all modules) in ./processm.launcher/target. The jar can be run with: `java -jar launcher-0.1-SNAPSHOT-jar-with-dependencies.jar`

#### Creating Docker image

Docker Desktop is required to be installed.

```shell
mvn docker:build
```

#### Exporting the image to file and uploading it

Instead of pushing the image to a remote repository we transfer the file directly to the server.

```shell
docker save -o processm-docker-image.tar processm && scp ./processm-docker-image.tar <server-address>:~
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
