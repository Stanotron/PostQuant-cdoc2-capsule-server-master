#!/usr/bin/env bash

# build Docker images locally
PROJECT_DIR=$(pwd)

bash build-get-server-image.sh
bash build-put-server-image.sh

cd $PROJECT_DIR/server-db
bash build-image.sh

cd $PROJECT_DIR