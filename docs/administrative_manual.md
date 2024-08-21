TODO: just random sentences for now

To run the system from the deployment package, extract the archive
`./processm.launcher/target/processm-<version>.tar.xz`
and execute command `./processm.sh` (linux/macOS) or `processm.bat` (Windows).

## Applying deployment package

On the server:

### Configuring database access

Make sure the environment variable `PROCESSM_CORE_PERSISTENCE_CONNECTION_URL` is set with database connection string.

### Loading the image into Docker

Instead of pulling the image from a remote repository we are importing it from the uploaded file.

```shell
docker load -i ./processm-docker-image.tar
```

### Run

```shell
docker run -d --name processm -p 2080:2080 -p 2443:2443 --env PROCESSM_CORE_PERSISTENCE_CONNECTION_URL processm
```

