# by default use docker.io as Docker registry, overwrite with
# --build-arg LIQUIBASE_IMAGE=custom.registry.io:8500/liquibase/liquibase
ARG LIQUIBASE_IMAGE=docker.io/liquibase/liquibase:4.29.2
FROM $LIQUIBASE_IMAGE
USER liquibase

WORKDIR /liquibase/changelog

COPY changelog/db.changelog-master.yaml ./
# create dir structure as specified in changelog file above
COPY changelog/changes ./db/changelog/changes

COPY liquibase.properties.docker ./liquibase.properties

CMD ["sh", "-c", "liquibase --url=$DB_URL --username=${DB_USER} --password=${DB_PASSWORD} update"]
