# Administrative manual

## Running official Docker image

TL;DR: Run the following command and enjoy fully-fledged ProcessM with configuration and data persistence.
```shell
docker run -d -p 80:2080 -p 443:2443 --name processm processm/processm-server-full:latest 
```
This will bring up a new Docker container named `processm` with a random database password, exposing ProcessM on the host 
ports 80 (HTTP) and 443 (HTTPS with a self-signed certificate) and no data persistence exceeding the lifetime of the container.

### Volumes

The ProcessM Docker image exposes three Docker volumes:

* `/processm/conf` where the configuration lives,
* `/processm/data` where the internal data of ProcessM are,
* `/var/lib/postgresql/data` where the TimescaleDB (PostgreSQL) database is stored.

You may need to access the content of the `/processm/conf` from the host system to adjust the ProcessM configuration (e.g., add SSL certificate).
Follow the Docker documentation on how to access a volume (see, e.g., [the Docker Desktop documentation](https://docs.docker.com/desktop/use-desktop/volumes/)).

The `/var/lib/postgresql/data` volume is maintained by the TimescaleDB base image
[timescale/timescaledb:latest-pg16-oss](https://hub.docker.com/r/timescale/timescaledb/). 
See [its description](https://hub.docker.com/r/timescale/timescaledb) for more information.

To expose the volumes to the host environment, add the following to the `docker run` command:
```shell
--mount source=processm_conf,target=/processm/conf --mount source=processm_data,target=/processm/data --mount source=processm_db,target=/var/lib/postgresql/data
```
This will create three volumes: `processm_conf`, `processm_data`, and `processm_db` that will ensure persistence of the 
configuration and data, and enable re-creation of the container without a data loss.

You can safely reuse the volumes between different ProcessM containers (one at a time). Neither the database, nor the configuration
will be overriden or removed.

### Ports

There three ports exposed by the container:

* 2080 offering unencrypted HTTP access to ProcessM;
* 2443 offering encrypted HTTPS access to ProcessM;
* 5432 with the direct access to TimescaleDB.

It is recommended to either use only 2443 with a proper SSL certificate, or to use 2080 and a HTTPS proxy in front of it.
Due to security concerns, unencrypted 2080 should never be exposed to a wide network, and especially not to the Internet.

### Environment

The container makes use of all the environment variables specified by the TimescaleDB base image
[timescale/timescaledb:latest-pg16-oss](https://hub.docker.com/r/timescale/timescaledb/). In particular, the following
environment variables are of note:
* `POSTGRES_DB` the name of the main ProcessM database, defaults to `processm`;
* `POSTGRES_USER` the name of the administrator user of the database, defaults to `postgres`;
* `POSTGRES_PASSWORD`, the password for the user. By default a strong random password is generated.
  * **Caveat 1**: If you bring up a container with a pre-populated `/var/lib/postgresql/data` volume, but with an empty 
    `/processm/conf` directory, you must specify the database password (and, optionally, `POSTGRES_DB` and `POSTGRES_USER`) 
    that will permit accessing the database. They will not overwrite the database configuration.
  * **Caveat 2**: Even if `/processm/conf` is populated, the environment variable `POSTGRES_PASSWORD` takes precedence
    and the configuration is updated accordingly. Hence, if you create a new container using an old configuration you
    must either leave `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` empty, or set them to old values.

Moreover, the container accepts the `MSMTPRC` variable, which is expected to contain configuration for the `msmtp` daemon 
(see below). If the file `/processm/conf/msmtprc` does not exist, the content of the variable will be written to the file.
Otherwise, the variable will be ignored.

Finally, any ProcessM configuration can be done using environment variables, as described in [the Configuration chapter](configuration.md), e.g.,
```shell
docker run -e PROCESSM_BASEURL=https://example.com/ ...
```

### Mail configuration

The image uses [msmtp](https://marlam.de/msmtp/) to send emails (e.g., for password recovery). It requires configuring by
creating the file `msmtprc` in the `/processm/conf` directory. You can use the following as a template:

```
account        gmail
auth           on
tls            on
host           smtp.gmail.com
port           465
tls_starttls   off
from           username@gmail.com
user           username
password       plain-text-password

account default: gmail
```

Adjust `host`, `user`, `password` (and, optionally, the other options) according to your needs.
The configuration can also be done when starting the container. Simply pass the content of the configuration file as
the environment variable `MSTPRC`. For convenience, you can prepare the configuration in a file (e.g., `msmtprc`), and
then use the shell to pass it as an environment variable:
```shell
docker run -e MSMTPRC="$(cat msmtprc)" ...
```

### Database configuration

The database configuration follows the defaults of the TimescaleDB image with one exception: the authentication method
for the TCP/IP connections from localhost to the database is changed from `trust` to `scram-sha-256`.

### SSL configuration

The SSL certificate must be in the JKS format. Follow the documentation available at 
[https://ktor.io/docs/server-ssl.html#configure-ssl-ktor](https://ktor.io/docs/server-ssl.html#configure-ssl-ktor)
to either generate such a certificate or convert a pre-existing PEM file.
Copy the JKS file to the `/processm/conf` volume, and update the following section in the `application.conf` file:
```
security {
    ssl {
      keyStore = null  // set default value to null; WebServicesHost will generate self-signed certificate if not present
      keyAlias = "ssl"
      keyStorePassword = ""
      privateKeyPassword = ""
    }
  }
```
`keyStore` must be replaced with the path to the JKS file (e.g., if the file is named `keystore.jks`, put `conf/keystore.jks` 
there). `keyAlias` and passwords must be consistent with the values you have used while creating the JKS file.

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
* [TimescaleDB](https://github.com/timescale/timescaledb) version 2.15 or newer.

For the details about installation of the dependencies please refer to the documentation of
[PostgreSQL](https://www.postgresql.org/docs/) and [TimescaleDB](https://docs.timescale.com/).

It is recommended to use preconfigured docker image
[timescale/timescaledb:latest-pg16](https://hub.docker.com/r/timescale/timescaledb/).

### Database setup

Once PostgreSQL and TimescaleDB extension are installed, please configure the database for ProcessM.
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




