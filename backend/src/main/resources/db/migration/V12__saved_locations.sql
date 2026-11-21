CREATE TABLE saved_location (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES user_account(id),
    name varchar(80) NOT NULL CHECK(length(trim(name))>0),
    latitude double precision NOT NULL CHECK(latitude BETWEEN -90 AND 90),
    longitude double precision NOT NULL CHECK(longitude BETWEEN -180 AND 180),
    radius_km double precision NOT NULL CHECK(radius_km BETWEEN 1 AND 1000),
    lookback_days integer NOT NULL CHECK(lookback_days BETWEEN 1 AND 30),
    monitoring_opt_in boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL,
    last_attempt_at timestamptz,
    baseline jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX saved_location_owner_idx ON saved_location(owner_id,created_at DESC,id);
CREATE TABLE saved_location_check (
    id uuid PRIMARY KEY,
    location_id uuid NOT NULL REFERENCES saved_location(id) ON DELETE CASCADE,
    checked_at timestamptz NOT NULL,
    payload jsonb NOT NULL
);
CREATE INDEX saved_location_check_idx ON saved_location_check(location_id,checked_at DESC,id);
