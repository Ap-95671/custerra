CREATE TABLE journey_area_evidence (
    journey_id uuid PRIMARY KEY REFERENCES monitored_journey(id) ON DELETE CASCADE,
    payload jsonb NOT NULL
);
