#!/usr/bin/env bash
set -euo pipefail

psql -v ON_ERROR_STOP=1 -v keycloak_password="$KEYCLOAK_DB_PASSWORD" --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
CREATE ROLE keycloak LOGIN PASSWORD :'keycloak_password';
CREATE DATABASE keycloak OWNER keycloak;
SQL
