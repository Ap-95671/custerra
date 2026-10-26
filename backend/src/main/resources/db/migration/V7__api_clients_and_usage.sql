CREATE TABLE api_organization (
 id uuid PRIMARY KEY,
 owner_id uuid NOT NULL UNIQUE REFERENCES user_account(id),
 plan varchar(20) NOT NULL DEFAULT 'DEVELOPMENT' CHECK(plan IN ('DEVELOPMENT','STANDARD')),
 created_at timestamptz NOT NULL
);
CREATE TABLE api_client (
 id uuid PRIMARY KEY,
 organization_id uuid NOT NULL REFERENCES api_organization(id) ON DELETE CASCADE,
 name varchar(80) NOT NULL,
 key_hash char(64) NOT NULL UNIQUE,
 key_suffix varchar(8) NOT NULL,
 created_at timestamptz NOT NULL,
 expires_at timestamptz NOT NULL,
 revoked_at timestamptz,
 last_used_at timestamptz
);
CREATE INDEX api_client_org_idx ON api_client(organization_id);
CREATE TABLE api_usage_bucket (
 organization_id uuid NOT NULL REFERENCES api_organization(id) ON DELETE CASCADE,
 window_start timestamptz NOT NULL,
 window_kind varchar(8) NOT NULL CHECK(window_kind IN ('DAY','MINUTE')),
 requests integer NOT NULL CHECK(requests>=0),
 PRIMARY KEY(organization_id,window_start,window_kind)
);
CREATE TABLE api_usage_record (
 id uuid PRIMARY KEY,
 client_id uuid NOT NULL REFERENCES api_client(id) ON DELETE CASCADE,
 started_at timestamptz NOT NULL,
 endpoint varchar(40) NOT NULL,
 status integer,
 duration_ms bigint
);
CREATE INDEX api_usage_client_time_idx ON api_usage_record(client_id,started_at DESC);
