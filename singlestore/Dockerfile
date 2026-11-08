# syntax=docker/dockerfile:1
# Thin layer on top of the official SingleStore Dev Image.
# The image natively runs an init SQL file on first start (path set by INIT_SQL),
# so all we do is bake ours in. SingleStore recommends pinning an explicit tag:
# https://github.com/singlestore-labs/singlestoredb-dev-image/pkgs/container/singlestoredb-dev/versions
ARG S2_IMAGE_TAG=latest
FROM ghcr.io/singlestore-labs/singlestoredb-dev:${S2_IMAGE_TAG}

COPY --chmod=644 init.sql /init.sql
ENV INIT_SQL=/init.sql
