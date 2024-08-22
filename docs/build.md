# Development guide

## Requirements

Before running build, make sure that the following dependencies are installed and working:

* [Java Development Kit 17](https://adoptium.net/temurin/releases/?version=17/)
* [Apache Maven 3.9](https://maven.apache.org/download.cgi)
* [Docker 27](https://www.docker.com/)
* [PostgreSQL 16](https://www.postgresql.org/download/) + [TimeScaleDB 2.15](https://www.docker.com/) (not required for
  build, but required for testing and development)

The remaining dependencies will be downloaded and installed automatically by the build system.

## Build and deploy from sources

The build system distinguishes the development and production builds. The development package has debugging symbols
and code maps compiled in, while the production build is striped out of them. By default, it compiles the development
package and appends `-dev` suffix to the version number. To create production package, enable `production` profile in
Maven.

To create the development package of ProcessM from sources, execute the following command in the main directory:

```shell
mvn clean install -DskipTests=true -T1C
```

To create the production package, execute:

```shell
mvn clean install -DskipTests=true -T1C -P production
```

The above commands produce a deployment package `./processm.launcher/target/processm-<version>.tar.xz`, install
all Java dependencies and compiled project Java modules in the local Maven repository. It also produces and installs in
the local Docker store the self-contained image `processm:<version>`, where `<version>` is a placeholder for ProcessM
version.

Follow the [administrative manual](administrative_manual.md) for configuration and running instructions.

Omit the `-DskipTests=true` argument to run full pipeline of building and testing. Note that the testing phase is time
consuming (takes even over an hour on modern machines) and requires the database connection to be configured. Refer to
the [administrative manual](administrative_manual.md) for the configuration of a database connection.

### Export the Docker image to file

While using the custom-built docker image, it may be useful to save it to a file for the use in another host.
To do so, run the command:

```shell
docker save -o processm-<version>-docker.tar processm:<version>
```

where `<version>` is a placeholder for ProcessM version.

To run this custom image on another machine, first load it into docker store using command:

```shell
docker load -i ./processm-<version>-docker.tar
```

Then, follow the [administrative manual](administrative_manual.md) for configuration and running instructions.

## Set up the development environment

To develop ProcessM, we recommend using [IntelliJ IDEA](https://www.jetbrains.com/idea/) in the Ultimate version due
to the support of web development. To run and debug ProcessM, a
running [TimeScaleDB](https://github.com/timescale/timescaledb)
instance is required. We recommend using
the [TimeScaleDB docker image](https://hub.docker.com/r/timescale/timescaledb/).
