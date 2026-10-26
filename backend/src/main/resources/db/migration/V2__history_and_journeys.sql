-- Immutable historical evidence snapshots; payload includes normalized catalog events and query/model version.
CREATE TABLE history_snapshot (
    id uuid PRIMARY KEY,
    fetched_at timestamptz NOT NULL,
    payload text NOT NULL
);
CREATE TABLE monitored_journey (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL CHECK (length(trim(name)) > 0),
    status varchar(10) NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED')),
    route text NOT NULL,
    created_at timestamptz NOT NULL,
    last_attempt_at timestamptz,
    last_checked_at timestamptz,
    last_error varchar(40),
    assessment text
);
CREATE INDEX journey_due_idx ON monitored_journey(last_attempt_at) WHERE status = 'ACTIVE';
CREATE TABLE journey_seen (
    journey_id uuid NOT NULL REFERENCES monitored_journey(id) ON DELETE CASCADE,
    source varchar(80) NOT NULL,
    external_id varchar(200) NOT NULL,
    PRIMARY KEY (journey_id, source, external_id)
);
CREATE TABLE journey_notice (
    id uuid PRIMARY KEY,
    journey_id uuid NOT NULL REFERENCES monitored_journey(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    source varchar(80) NOT NULL,
    external_id varchar(200) NOT NULL,
    payload text NOT NULL,
    UNIQUE(journey_id, source, external_id)
);
CREATE INDEX journey_notice_time_idx ON journey_notice(journey_id, created_at DESC, id);
