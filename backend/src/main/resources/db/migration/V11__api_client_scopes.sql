-- Existing keys retain their three previously available operations.
ALTER TABLE api_client ADD COLUMN scopes text NOT NULL DEFAULT 'location,nearby,route';
ALTER TABLE api_client ADD CONSTRAINT api_client_scopes_valid CHECK (
    scopes<>'' AND string_to_array(scopes,',') <@ ARRAY['nearby','location','route']::text[]
);
