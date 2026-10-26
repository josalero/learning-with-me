CREATE TABLE job_application (
    id varchar(64) PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    role_title varchar(120) NOT NULL,
    stage varchar(32) NOT NULL,
    band_min numeric(12, 2) NOT NULL,
    band_max numeric(12, 2) NOT NULL
);

CREATE TABLE hiring_action (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    application_id varchar(64) NOT NULL REFERENCES job_application (id),
    action varchar(32) NOT NULL,
    from_stage varchar(32) NOT NULL,
    to_stage varchar(32) NOT NULL,
    reason varchar(64),
    idempotency_key varchar(200) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL
);

CREATE TABLE interview_scorecard (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    application_id varchar(64) NOT NULL REFERENCES job_application (id),
    round varchar(16) NOT NULL,
    recommendation varchar(16) NOT NULL,
    problem_solving smallint NOT NULL CHECK (problem_solving BETWEEN 1 AND 4),
    code_quality smallint NOT NULL CHECK (code_quality BETWEEN 1 AND 4),
    communication smallint NOT NULL CHECK (communication BETWEEN 1 AND 4),
    role_depth smallint NOT NULL CHECK (role_depth BETWEEN 1 AND 4),
    idempotency_key varchar(200) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL
);

CREATE TABLE job_offer (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    application_id varchar(64) NOT NULL UNIQUE REFERENCES job_application (id),
    level varchar(8) NOT NULL,
    base_salary numeric(12, 2) NOT NULL,
    status varchar(16) NOT NULL,
    idempotency_key varchar(200) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    responded_at timestamptz
);

CREATE TABLE agent_session (
    session_id varchar(64) PRIMARY KEY,
    tenant_id varchar(64) NOT NULL,
    owner_name varchar(64) NOT NULL,
    provider varchar(32) NOT NULL,
    compatibility_mode varchar(40) NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE tool_approval (
    id uuid PRIMARY KEY,
    session_id varchar(64) NOT NULL REFERENCES agent_session (session_id),
    tenant_id varchar(64) NOT NULL,
    requested_by varchar(64) NOT NULL,
    paused_reply_event_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    version bigint NOT NULL,
    risk_level varchar(16) NOT NULL,
    trace_id varchar(64),
    span_id varchar(32),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    decided_by varchar(64),
    decided_at timestamptz,
    idempotency_key varchar(200),
    answer text
);

CREATE UNIQUE INDEX tool_approval_idempotency_uk
    ON tool_approval (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX tool_approval_session_status_idx
    ON tool_approval (session_id, status);

CREATE TABLE tool_call_decision (
    id uuid PRIMARY KEY,
    approval_id uuid NOT NULL REFERENCES tool_approval (id),
    tool_call_key varchar(80) NOT NULL,
    tool_name varchar(80) NOT NULL,
    arguments_json text NOT NULL,
    arguments_hash varchar(64) NOT NULL,
    decision varchar(16) NOT NULL,
    deny_message varchar(500),
    execution_status varchar(32),
    error_code varchar(64),
    CONSTRAINT tool_call_decision_uk UNIQUE (approval_id, tool_call_key)
);

INSERT INTO job_application (id, tenant_id, role_title, stage, band_min, band_max) VALUES
    ('APP-1', 'tenant-a', 'Backend Engineer', 'RECRUITER_SCREEN', 120000, 160000),
    ('APP-2', 'tenant-b', 'Support Engineer', 'APPLIED', 70000, 90000);
