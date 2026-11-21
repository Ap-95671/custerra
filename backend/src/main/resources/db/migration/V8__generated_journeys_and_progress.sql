-- Preserve existing PAUSED/ACTIVE records and manual route payloads.
ALTER TABLE monitored_journey DROP CONSTRAINT monitored_journey_status_check;
ALTER TABLE monitored_journey ADD CONSTRAINT monitored_journey_status_check
    CHECK(status IN ('PLANNED','ACTIVE','PAUSED','COMPLETED','CANCELLED'));
ALTER TABLE monitored_journey ADD COLUMN encoded_geometry text CHECK(length(encoded_geometry)<=200000);
ALTER TABLE monitored_journey ADD COLUMN geometry_version integer CHECK(geometry_version=1);
ALTER TABLE monitored_journey ADD COLUMN origin_name varchar(120);
ALTER TABLE monitored_journey ADD COLUMN destination_name varchar(120);
ALTER TABLE monitored_journey ADD COLUMN progress text;
ALTER TABLE monitored_journey ADD COLUMN progress_at timestamptz;
ALTER TABLE monitored_journey ADD CONSTRAINT journey_geometry_pair CHECK ((encoded_geometry IS NULL) = (geometry_version IS NULL));
ALTER TABLE monitored_journey ADD CONSTRAINT journey_progress_pair CHECK ((progress IS NULL) = (progress_at IS NULL));
CREATE INDEX journey_progress_expiry_idx ON monitored_journey(progress_at) WHERE progress_at IS NOT NULL;
