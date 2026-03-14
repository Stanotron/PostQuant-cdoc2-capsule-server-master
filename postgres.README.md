## Create postgres instance inside docker

```
docker run --name cdoc2-psql -p 5432:5432 -e POSTGRES_DB=cdoc2 -e POSTGRES_PASSWORD=secret -d postgres

docker start cdoc2-psql
docker stop cdoc2-psql
```
#docker rm cdoc2-psql


## Create cdoc2 database

Download [cdoc2-server-liquibase](https://github.com/orgs/open-eid/packages?ecosystem=container) image 
(version must match server version) that contains liquibase changeset files
specific to server version and create a `cdoc2` database. If database is running inside Docker, then
`--link` is required, so that liquibase container can connect to it.
```
docker run --rm --link cdoc2-psql \
  --env DB_URL=jdbc:postgresql://cdoc2-psql/cdoc2 \
  --env DB_PASSWORD=secret \
  --env DB_USER=postgres \
  ghcr.io/open-eid/cdoc2-server-liquibase:v1.4.1-rc.1-74cbc827e3cf08c2f4a51711a2072b6344f9aee1
```

or use standard liquibase command:

```
docker run --rm --link cdoc2-psql \
ghcr.io/open-eid/cdoc2-server-liquibase:v1.4.1-rc.1-74cbc827e3cf08c2f4a51711a2072b6344f9aee1 \
  --url jdbc:postgresql://cdoc2-psql/cdoc2 \
  --username=postgres \
  --password=secret \
  --defaultsFile=liquibase.properties \
update
```

Can also be used to update DB running in other host by changing `--url`, `--username` and `--password` parameters. 
Then `--link` is not required.

More info https://hub.docker.com/r/liquibase/liquibase