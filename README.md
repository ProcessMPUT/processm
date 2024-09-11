# ProcessM

ProcessM is an artificial intelligence tool supporting business process management. ProcessM seamlessly integrates with
variety of data sources such as database systems and ERP tools using fully-configurable ETL processes. ProcessM models
he business process in online mode based on the incoming in real-time events from the data sources system, verifies the
conformance of the process with the model, classifies detected deviations from the model into errors and concept drift,
reports deviations together with a root cause analysis and calculates KPIs for performance analysis and bottleneck
identification. ProcessM is a web-service that works in a continuous mode and does the above tasks unattended.
Detected deviations in the operation of the process are immediately reported to the user. The web-service of ProcessM
is compatible with Windows, Linux and macOS, and the client of ProcessM works in a web-browser running on virtually
any operating system.

## Getting started

### Installation

ProcessM can be deployed as a Docker image or a standalone Java package. We recommend using the official Docker image,
as it includes all runtime dependencies and simply runs with a single command. For environments without the
virtualization and Docker support, one can still use the Java binaries, but it requires manual installation and
configuration of dependencies.

#### Docker image

For Docker users, download and run ProcessM by simply executing the following command:

```bash
docker run -d -p 80:2080 -p 443:2443 --name processm processm/processm-server-full:latest
```

#### Local installation

For non-containerized installation, download the official binary package from the
[ProcessM releases page](https://github.com/ProcessMPUT/processm/releases) and then follow the
[administrative manual](docs/administrative_manual.md) for the details of configuration and running.

### Basic usage

This guide provides quick steps to accomplish basic process mining tasks in ProcessM.

#### Create a user and an organization

Initially, ProcessM has no users or organizations configured. To create the first user, select the `Register` option on
the login screen, enter the username and password, and choose the option to create a new organization for the user.
Once the user is created, you can log in to the system.

#### Create a data store

Most features of ProcessM require data store.
A data store keeps event logs, configuration for data connectors to remote data sources, and configuration of ETL
processes. The data stores are recommended to correspond to projects and/or organizational units.

To create data store, open the Data stores view and click `Add new` button. Type name for the new data store. Once
added,
enter the data store configuration view by clicking gear-like icon. In this view, you can upload an event log in the
eXtensible Event Stream (XES) format. Use e.g., the exemplary logs from the
[ProcessM repository](https://github.com/ProcessMPUT/processm/tree/master/xes-logs).
This view also enables you to configure data connector to an external database and an Extraction, Transformation, Load
(ETL) process for the retrieval of event log from this database.
Common database management systems are supported, including PostgreSQL, Oracle, MySQL, SQLServer, MongoDB, CouchDB.

#### Create a workspace

Workspace is like a configurable dashboard. You can put process models, KPI values, event log views, and other
components into the workspace and flexibly configure their layout.

To add workspace, navigate to the Workspaces view and click the plus button at the bottom pane. To add a component,
click the `Add new component` button at the top of the workspace and select component type. For each component, you are
required to set name, data store for event logs that you work with, and a [PQL](docs/pql.md) query to retrieve events
for the calculation of process model, KPI, or log view. Unlock the workspace layout using the `Unlock the area` button,
and lay out components for your convenience.

The content of the components calculates in the background and refreshes automatically, as new data matching the PQL
query arrives. You will be notified for acceptance of the changes in the process models.

ProcessM also calculates automatically common KPIs for parts of the process models: activities, and arcs. They consist
of lead, service, waiting times, running costs, etc. To observe non-conformance of a process model with data, click the
`Alignments` button at the top of process model. Alignments are available only for process models with executable
semantics, e.g., Causal nets and Petri nets.

#### Share your work and configure access control

Use the Users view, to add users, groups, and suborganizations of your organization.
The access to data stores and workspaces can be restricted at fine-grained level using access control lists (ACL). To
access the ACL for a specific object click the security shield icon of this object. By default, you are the owner of
the object that you have created. Add new access control entries to grant access to other users. You can select specific
users, groups, or organizations, and grant them read, write, and owner permissions. The user is granted with the
highest permission that results from the ACL.

