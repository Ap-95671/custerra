-- Observations remain source-scoped. This ledger belongs to a journey, not a global incident catalog.
CREATE TABLE journey_evidence_revision (
    journey_id uuid NOT NULL REFERENCES monitored_journey(id) ON DELETE CASCADE,
    source varchar(80) NOT NULL,
    external_id varchar(200) NOT NULL,
    state_version bigint NOT NULL,
    recorded_at timestamptz NOT NULL DEFAULT now(),
    present boolean NOT NULL,
    evidence text NOT NULL,
    PRIMARY KEY(journey_id,source,external_id,state_version)
);
CREATE INDEX journey_revision_time_idx ON journey_evidence_revision(journey_id,recorded_at DESC,source,external_id,state_version);
-- Preserve only actual stored current evidence; do not fabricate earlier revisions.
INSERT INTO journey_evidence_revision(journey_id,source,external_id,state_version,present,evidence)
SELECT journey_id,source,external_id,state_version,present,evidence FROM journey_seen WHERE evidence IS NOT NULL;
