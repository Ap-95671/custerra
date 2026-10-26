# Database — Milestone 2

PostgreSQL stores one domain table, `hazard_event`, created by the first Flyway migration under `backend/src/main/resources/db/migration`. Flyway also maintains `flyway_schema_history`. No source, user, journey, notification or risk tables are created yet.

The primary key is a JPA-generated UUID. `(source, external_id)` is unique. Enums persist as names, not ordinals, with database check constraints. Coordinates use double precision; event/observation times use timestamps with time zone. Description, end time and affected radius are nullable. Checks reject invalid coordinates, radii, time order and blank required text. Indexes support newest-first reads and hazard-type filtering. No PostGIS extension is required for these queries.

Flyway migrates before Hibernate validates the schema. Automatic baselining and clean are disabled, and ordinary SQL initialization remains off. Migrations contain schema only; the empty `database/seeds/sample_disasters.sql` is not executed. Do not edit V1 after it has been applied; add a new migration for subsequent schema or supported-enum changes.

Run the opt-in PostgreSQL tests against an explicit dedicated test database as described in the README. Event fixtures live exclusively in test code and roll back. The test database retains its migrated schema for subsequent runs.
