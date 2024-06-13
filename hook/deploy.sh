#!/bin/sh
set -e

echo "Deploy script executed successfully"

env

DOMAINS_MAIN=`echo $RENEWED_DOMAINS|awk '{print $1}'`

rm -rf /srv/dst/$DOMAINS_MAIN || true
mkdir -p /srv/dst/$DOMAINS_MAIN || true
cp -rLv $RENEWED_LINEAGE/* /srv/dst/$DOMAINS_MAIN/