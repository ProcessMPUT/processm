# Data generator

## On the build host

1. Build the project. It is sufficient to build `processm.tools` only, as it is independent of 
   the other subprojects of processm.
1. Export the image `docker save 'processm/wwi' | gzip > wwi.tgz`
3. Copy to the target host, e.g., `scp wwi.tgz <target>:`

## On the target host

1. Remove the previous container: `docker rm wwi`
2. Remove the previous version of the image: `docker image rm processm/wwi`
3. Import the image: `zcat wwi.tgz |docker load`
4. Create and start the container for the first time: 
   `docker run -d --restart always -p 1433:1433 --env 'processm.tools.generator.customerOrderMinStepLength=10000' --env 'processm.tools.generator.customerOrderMaxStepLength=20000' --env 'processm.tools.generator.purchaseOrderMinStepLength=30000' --env 'processm.tools.generator.purchaseOrderMaxStepLength=60000' --name wwi 'processm/wwi'`
   For more information on the environment variables, see the documentation for `processm.tools.generator.Configuration`
   In particular, `--restart always` ensures that the container starts automatically at boot.
   

## Further remarks

As of 01.02.2022 the generator is configured at `util2.processm.cs.put.poznan.pl`