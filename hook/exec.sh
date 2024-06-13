#!/bin/sh
set -e

if [[ -z "$1" ]]; then
  nginx
  certbot renew -v
elif [[ -d "/etc/letsencrypt/live/$1"* ]]; then
  echo "$1 => installed Skip"
else
  nginx
  certbot certonly --manual --preferred-challenges=http --register-unsafely-without-email --agree-tos \
   --manual-auth-hook /srv/hook/auth.sh \
   --manual-cleanup-hook /srv/hook/cleanup.sh \
   --deploy-hook /srv/hook/deploy.sh \
   -d $@
fi