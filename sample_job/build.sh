#!/usr/bin/env sh

tag=latest
base=core.harbor.10.131.36.2.nip.io/oaas/counting

docker build -t $base:$tag .
docker push $base:$tag
