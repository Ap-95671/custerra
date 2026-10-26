-- Preserve existing values and add provider advisories without promoting them to official warnings.
ALTER TABLE hazard_event DROP CONSTRAINT hazard_event_information_type_check;
ALTER TABLE hazard_event ADD CONSTRAINT hazard_event_information_type_check CHECK (information_type IN (
    'OBSERVED_EVENT', 'ADVISORY', 'OFFICIAL_WARNING', 'MODELED_RISK', 'EXPERIMENTAL_PREDICTION'));
