# Configuration reference

ProcessM loads configuration at startup from the `conf` directory and environment variables and ignores any changes
in options occuring in runtime. To reload configuration, please restart ProcessM. Both file-based configuration and
environment variable-based configuration supports the same options. The options configured in files take prcedence
over the same options configured using environment variables.

All user-accessible configuration options are stored in the `conf` directory. The main configuration file is
`config.properties`. The configrable properties follow the format `key=value`, where `key` id dot-saparated hierarchical
property name, and `value` is the property value. Lines starting with the hash `#` symbol are comments.

The same properties can be loaded from the environment variables, however, due to the
[limitations of the operating systems](https://pubs.opengroup.org/onlinepubs/9799919799/basedefs/V1_chap08.html#tag_08)
all letters in keys must be converted to uppercase and dots replaced with underscores `_`. For instance of database
connection string, the valid setting in `config.properties` is given by line:

```properties
processm.core.persistence.connection.URL=jdbc:postgresql://host:port/database?user=user&password=password
```

while the same setting as environment variable is given by:

```properties
PROCESSM_CORE_PERSISTENCE_CONNECTION_URL=jdbc:postgresql://host:port/database?user=user&password=password
```

The following reference refers to the keys in the configuration files. Use the above-mentioned key mapping for the use
of environment variable-based configuration.

## config.properties file

### processm.core.persistence.connection.URL

A valid JDBC connection string to the PostgreSQL database with TimeScaleDB extension.

```properties
processm.core.persistence.connection.URL=jdbc:postgresql://host:port/database?user=user&password=password
```

where `host` and `port` point at a PostgreSQL instance, `database` is the name of the main database. `user` and
`password` are credentials for accessing the database.

### processm.services.timeToResetPassword

Time amount in minutes for the user to click on the link to reset their password

```properties
processm.services.timeToResetPassword=60
```

### processm.baseUrl

The base URL of the ProcessM instance. Used in e.g. emails.

```properties
processm.baseUrl=http://localhost:2080/
```

### processm.email.useSMTP

Send emails via SMTP if true, otherwise via sendmail (the default).

```properties
processm.email.useSMTP=false
```

### processm.email.sendmailExecutable

The path to a sendmail-compatible executable to send emails. Used if `processm.email.useSMTP=false`.

```properties
processm.email.sendmailExecutable=/usr/sbin/sendmail
```

### processm.email.defaultFrom

The default value for the sender full name

```properties
processm.email.defaultFrom=ProcessM
```

### processm.email.envelopeSender

The envelope sender address

```properties
processm.email.envelopeSender=processm@example.com
```

### processm.etl.debezium.persistence.directory

The directory to store files required by automatic ETL processes and passed to Debezium using
`offset.storage.file.filename` and `database.history.file.filename`. By default the current directory.

```properties
processm.etl.debezium.persistence.directory=.
```

### processm.logs.limit.*

The maximum numbers of logs, traces, events, respectively, returned by the PQL query in the PQL interpreter view. The
`processm.logs.limit.downloadLimitFactor` property is a multipier for these limits that applies to event log files
downloaded from the PQL interpreter view.

```properties
processm.logs.limit.log=10
processm.logs.limit.trace=30
processm.logs.limit.event=90
processm.logs.limit.downloadLimitFactor=10
```

### processm.enhancement.kpi.alignerkpi.queueDelayMs

The delay in milliseconds before an alignment calculation job starts. As alignment calculation is time-consuming,
this delay sets the time for collecting ongoing events and changes in the process model and event log before actually
running calculations.

```properties
processm.enhancement.kpi.alignerkpi.queueDelayMs=5000
```

### processm.demoMode

Controls whether ProcessM runs in the demo mode. It disables some security features. Set to false (the default) for
production environments.

```properties
processm.demoMode=false
```

### processm.testMode

Controls whether ProcessM runs in the test mode. For development purposes only. Set to false (the default) for
production environments.

```properties
processm.testMode=false
```
