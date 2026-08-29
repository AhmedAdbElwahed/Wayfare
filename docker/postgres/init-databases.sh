#!/bin/sh
# The official postgres image only creates one database (POSTGRES_DB) at
# startup. Each Wayfare service owns its own database (see the datasource
# urls in config-repo/*.yml), so create the rest here.
set -e

for db in wayfare_auth wayfare_rider; do
  psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" <<-EOSQL
    SELECT 'CREATE DATABASE $db OWNER $POSTGRES_USER'
    WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = '$db')\gexec
EOSQL
done
