#!/usr/bin/env bash
# build cdoc2-server-liquibase locally

GET_SERVER_VERSION=$(cd .. && mvn help:evaluate -f get-server -Dexpression=project.version -q -DforceStdout)
DOCKER_REGISTRY=ghcr.io
DOCKER_REPOSITORY=open-eid

LIQUIBASE_IMAGE_NAME=cdoc2-server-liquibase

# version shows what version of get-server is used in pair with liquibase image
# Docker version should be same as get-server-version although server-db pom version might be different
docker build -t ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/${LIQUIBASE_IMAGE_NAME}:${GET_SERVER_VERSION} ../server-db/src/main/resources/db
docker tag ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/${LIQUIBASE_IMAGE_NAME}:${GET_SERVER_VERSION}  ${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/${LIQUIBASE_IMAGE_NAME}:latest
