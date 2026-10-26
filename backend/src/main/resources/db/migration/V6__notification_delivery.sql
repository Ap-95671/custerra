CREATE TABLE notification_preference (
    owner_id uuid PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    enabled boolean NOT NULL DEFAULT true,
    push_enabled boolean NOT NULL DEFAULT false,
    hazard_types text NOT NULL,
    minimum_severity varchar(20) NOT NULL DEFAULT 'UNKNOWN',
    include_unknown boolean NOT NULL DEFAULT true
);
CREATE TABLE push_subscription (
    id uuid PRIMARY KEY,
    owner_id uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    endpoint_hash varchar(64) NOT NULL UNIQUE,
    endpoint text NOT NULL,
    p256dh varchar(100) NOT NULL,
    auth_secret varchar(40) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX push_subscription_owner_idx ON push_subscription(owner_id);
CREATE TABLE notification_item (
    id uuid PRIMARY KEY REFERENCES journey_notice(id) ON DELETE CASCADE,
    owner_id uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    journey_id uuid NOT NULL REFERENCES monitored_journey(id) ON DELETE CASCADE,
    created_at timestamptz NOT NULL,
    read_at timestamptz,
    payload text NOT NULL
);
CREATE INDEX notification_owner_time_idx ON notification_item(owner_id,created_at DESC,id);
CREATE TABLE notification_delivery (
    id uuid PRIMARY KEY,
    notification_id uuid NOT NULL REFERENCES notification_item(id) ON DELETE CASCADE,
    subscription_id uuid REFERENCES push_subscription(id) ON DELETE SET NULL,
    status varchar(20) NOT NULL CHECK(status IN ('PENDING','SENDING','SENT','FAILED','UNCERTAIN','CANCELLED')),
    attempts integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz NOT NULL,
    lease_until timestamptz,
    last_error varchar(60),
    UNIQUE(notification_id,subscription_id)
);
CREATE INDEX notification_delivery_due_idx ON notification_delivery(next_attempt_at) WHERE status='PENDING';
CREATE TABLE notification_attempt (
    delivery_id uuid NOT NULL REFERENCES notification_delivery(id) ON DELETE CASCADE,
    attempt integer NOT NULL,
    started_at timestamptz NOT NULL,
    completed_at timestamptz,
    result varchar(60),
    PRIMARY KEY(delivery_id,attempt)
);
