#!/usr/bin/env bash

# build Docker images for get-server, put-server and

# build Docker image locally
#set -x

PUT_SERVER_VERSION=$(mvn help:evaluate -f put-server -Dexpression=project.version -q -DforceStdout)

DOCKER_REGISTRY=ghcr.io
DOCKER_REPOSITORY=open-eid
IMAGE_NAME=$(mvn help:evaluate -f put-server -Dexpression=project.artifactId -q -DforceStdout)

mvn install -Dmaven.test.skip=true

mvn spring-boot:build-image -f put-server \
-Dmaven.test.skip=true \
-Dspring-boot.build-image.publish=false \
-Dspring-boot.build-image.imageName=${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/${IMAGE_NAME}:${PUT_SERVER_VERSION} \
-Dspring-boot.build-image.tags=${DOCKER_REGISTRY}/${DOCKER_REPOSITORY}/${IMAGE_NAME}:latest \
-Dspring-boot.build-image.createdDate=now
