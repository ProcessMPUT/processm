# Administrative manual

## Running official Docker image

TODO: #211 describe all docker options that apply; explain how to configure processm; explain how to ensure
persistance/export data out of container

```bash
docker run -p 80:2080 -p 443:2443 -d --name processm processm/processm-server-full:latest
```

```shell
docker run -d --name processm -p 2080:2080 -p 2443:2443 --env PROCESSM_CORE_PERSISTENCE_CONNECTION_URL processm
```

## Running custom Docker image

Instead of pulling the image from the Docker hub, you can load a custom Docker image created in the custom
[build](build.md) using command:

```shell
docker load -i ./processm-<version>-docker.tar
```

where `<version>` is a placeholder for ProcessM version.

And run it the same way as the official image using command:
```shell
docker run -p 80:2080 -p 443:2443 -d --name processm processm/processm-server-full:latest
```

## Standalone installation

### Requirements

For standalone installation, please first install runtime dependencies:

* [PostgreSQL](https://www.postgresql.org/) 16, and its extension:
* [TimeScaleDB](https://github.com/timescale/timescaledb) version 2.15 or newer.

For the details about installation of the dependencies please refer to the documentation of
[PostgreSQL](https://www.postgresql.org/docs/) and [TimeScaleDB](https://docs.timescale.com/).

It is recommended to use preconfigured docker image
[timescale/timescaledb:latest-pg16](https://hub.docker.com/r/timescale/timescaledb/).

### Database setup

Once PostgreSQL and TimeScaleDB extension are installed, please configure the database for ProcessM.
Create the `processm` role with the login and superuser privileges and create empty database using SQL commands:

```sql
CREATE ROLE processm LOGIN SUPERUSER PASSWORD 'your_password';
CREATE DATABASE processm WITH OWNER processm;
```

where `your_password` is a password. We recommend using strong passwords.

The superuser priviledge is required because ProcessM creates automatically separate databases for every data store
configured by a user and configures required PostgreSQL extensions. Hence, it is recommended to set up a separated,
isolated instance of PostgreSQL dedicated to ProcessM only.

### Installation

To install ProcessM, please extract the binary package [
`processm-<version>.tar.xz`](https://github.com/ProcessMPUT/processm/releases)
to the desired location. On Linux and macOS use the terminal command:

```bash
tar -xf processm-<version>.tar.xz
```

On Windows use an archiver tool like [7-Zip](https://7-zip.org/).

### Configuration

Before the first run it may be required to set basic configuration options, e.g., database connection parameters. All
configuration files are stored in the `conf` directory in the main ProcessM directory. The main configuration file is
`config.properties`. Edit this file using a text editor, e.g., vim on Linux/macOS or Notepad on Windows.

To set up database connection, uncomment if necessary and set the property `processm.core.persistence.connection.URL` to
a valid [JDBC connection string](https://jdbc.postgresql.org/documentation/use/#connection-parameters). For instance:

```properties
processm.core.persistence.connection.URL=jdbc:postgresql://host:port/database?user=user&password=password
```

where `host` and `port` point at a PostgreSQL instance, `database` is the name of the main database. `user` and
`password` are credentials for accessing the database. These values must be consistent with the values configured in
the [database setup](#Database-setup) step.

Please also set the `processm.baseUrl` property to a valid URL of the ProcessM instance:

```properties
processm.baseUrl=http://localhost:2080/
```

For the reference of all configuration options please refer to [documentation](configuration.md).




