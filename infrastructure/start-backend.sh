#!/bin/sh
set -eu
if [ -z "${DB_URL:-}" ]; then
  : "${DB_HOST:?DB_HOST is required}" "${DB_NAME:?DB_NAME is required}"
  export DB_URL="jdbc:postgresql://${DB_HOST}:${DB_PORT:-5432}/${DB_NAME}?sslmode=${DB_SSL_MODE:-verify-full}&sslfactory=org.postgresql.ssl.DefaultJavaSSLFactory"
fi
exec java -XX:MaxRAMPercentage=70 -jar /app/backend.jar
