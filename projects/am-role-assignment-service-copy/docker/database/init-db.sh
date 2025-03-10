#!/usr/bin/env bash

set -e

if [ -z "$ROLE_ASSIGNMENT_DB_USERNAME" ] || [ -z "$ROLE_ASSIGNMENT_DB_PASSWORD" ]; then
  echo "ERROR: Missing environment variable. Set value for both 'ROLE_ASSIGNMENT_DB_USERNAME' and 'ROLE_ASSIGNMENT_DB_PASSWORD'."
  exit 1
fi

# Create role and database
psql -v ON_ERROR_STOP=1 --username postgres --set USERNAME=$ROLE_ASSIGNMENT_DB_USERNAME --set PASSWORD=$ROLE_ASSIGNMENT_DB_PASSWORD <<-EOSQL
  CREATE USER :USERNAME WITH PASSWORD ':PASSWORD';
  CREATE DATABASE role_assignment
    WITH OWNER = :USERNAME
    ENCODING = 'UTF-8'
    CONNECTION LIMIT = -1;
  ALTER SCHEMA public OWNER TO :USERNAME;

EOSQL
