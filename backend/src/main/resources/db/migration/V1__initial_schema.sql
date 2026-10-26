CREATE TABLE hazard_event (
    id UUID PRIMARY KEY,
    external_id VARCHAR(200) NOT NULL CHECK (btrim(external_id) <> ''),
    hazard_type VARCHAR(40) NOT NULL CHECK (hazard_type IN (
        'EARTHQUAKE', 'FLOOD', 'LANDSLIDE', 'WILDFIRE', 'TSUNAMI', 'CYCLONE',
        'HURRICANE', 'TYPHOON', 'TORNADO', 'VOLCANIC_ERUPTION', 'SEVERE_WEATHER')),
    title VARCHAR(500) NOT NULL CHECK (btrim(title) <> ''),
    description VARCHAR(10000),
    severity VARCHAR(20) NOT NULL CHECK (severity IN ('UNKNOWN', 'INFO', 'LOW', 'MODERATE', 'HIGH', 'CRITICAL')),
    information_type VARCHAR(40) NOT NULL CHECK (information_type IN (
        'OBSERVED_EVENT', 'OFFICIAL_WARNING', 'MODELED_RISK', 'EXPERIMENTAL_PREDICTION')),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    affected_radius_km DOUBLE PRECISION CHECK (affected_radius_km BETWEEN 0 AND 20040),
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE,
    source VARCHAR(100) NOT NULL CHECK (btrim(source) <> ''),
    source_url VARCHAR(2000) NOT NULL CHECK (source_url ~ '^https?://[^[:space:]]+$'),
    observed_at TIMESTAMP WITH TIME ZONE NOT NULL,
    confidence VARCHAR(20) NOT NULL CHECK (confidence IN ('UNKNOWN', 'LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT uk_hazard_source_external UNIQUE (source, external_id),
    CONSTRAINT ck_hazard_time_range CHECK (ended_at IS NULL OR ended_at >= started_at)
);

CREATE INDEX idx_hazard_started ON hazard_event (started_at DESC, id DESC);
CREATE INDEX idx_hazard_type_started ON hazard_event (hazard_type, started_at DESC, id DESC);
