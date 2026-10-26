CREATE TABLE user_account (
    id uuid PRIMARY KEY,
    username varchar(40) NOT NULL UNIQUE,
    password_hash varchar(100) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
ALTER TABLE monitored_journey ADD COLUMN owner_id uuid REFERENCES user_account(id);
ALTER TABLE history_snapshot ADD COLUMN owner_id uuid REFERENCES user_account(id);
CREATE INDEX journey_owner_created_idx ON monitored_journey(owner_id,created_at DESC,id);
CREATE INDEX history_owner_idx ON history_snapshot(owner_id,id);
-- Existing installation data remains private and recoverable. Never assign it to the first registrant.
UPDATE monitored_journey SET status='PAUSED' WHERE owner_id IS NULL;
