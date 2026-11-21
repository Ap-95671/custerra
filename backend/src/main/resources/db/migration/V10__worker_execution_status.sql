CREATE TABLE worker_execution_status (
    job varchar(30) PRIMARY KEY CHECK(job IN ('journeys','notifications')),
    last_started_at timestamptz NOT NULL,
    last_completed_at timestamptz,
    outcome varchar(20) NOT NULL CHECK(outcome IN ('RUNNING','COMPLETED','DEGRADED','FAILED'))
);
