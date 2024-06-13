#!/bin/sh
set -e

env

sed -i  "/server /a location = \/.well-known\/acme-challenge\/$CERTBOT_TOKEN{return 200 $CERTBOT_VALIDATION;}" /etc/nginx/http.d/default.conf

nginx -s reload