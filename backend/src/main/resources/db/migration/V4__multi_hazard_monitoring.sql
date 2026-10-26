-- Preserve existing baselines and identities. New provider baselines are independent.
CREATE TABLE journey_provider_checkpoint (
    journey_id uuid NOT NULL REFERENCES monitored_journey(id) ON DELETE CASCADE,
    source varchar(80) NOT NULL,
    last_success_at timestamptz NOT NULL,
    PRIMARY KEY (journey_id, source)
);
INSERT INTO journey_provider_checkpoint(journey_id,source,last_success_at)
SELECT id,'USGS',last_checked_at FROM monitored_journey WHERE last_checked_at IS NOT NULL;
ALTER TABLE journey_seen ADD COLUMN fingerprint varchar(64);
ALTER TABLE journey_seen ADD COLUMN evidence text;
ALTER TABLE journey_seen ADD COLUMN state_version bigint NOT NULL DEFAULT 0;
ALTER TABLE journey_seen ADD COLUMN present boolean NOT NULL DEFAULT true;
ALTER TABLE journey_notice DROP CONSTRAINT journey_notice_journey_id_source_external_id_key;
ALTER TABLE journey_notice ADD COLUMN notice_type varchar(60) NOT NULL DEFAULT 'NEW_MATCHING_OBSERVATION';
ALTER TABLE journey_notice ADD COLUMN state_version bigint NOT NULL DEFAULT 0;
ALTER TABLE journey_notice ADD CONSTRAINT journey_notice_state_unique UNIQUE(journey_id,source,external_id,notice_type,state_version);
