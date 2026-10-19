CREATE TABLE execution (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    agent_id varchar(64) NOT NULL REFERENCES agent (id),
    principal_id varchar(64) NOT NULL REFERENCES principal (id),
    task_type varchar(64) NOT NULL,
    candidate_id varchar(64) NOT NULL,
    status varchar(32) NOT NULL,
    trace_id varchar(64) NOT NULL,
    verdict varchar(64),
    successful boolean,
    failure_reason varchar(64),
    retrieved_evidence_ids text[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL,
    completed_at timestamptz
);

CREATE TABLE execution_step (
    id uuid PRIMARY KEY,
    execution_id uuid NOT NULL REFERENCES execution (id),
    seq integer NOT NULL,
    type varchar(16) NOT NULL,
    tool_name varchar(128),
    decision varchar(32),
    reason_code varchar(64),
    args_hmac varchar(64),
    result_hmac varchar(64),
    input_tokens integer,
    output_tokens integer,
    cost_micros bigint,
    span_id varchar(32),
    created_at timestamptz NOT NULL,
    UNIQUE (execution_id, seq)
);

CREATE TABLE model_pricing (
    model_id varchar(128) PRIMARY KEY,
    input_micros_per_1k integer NOT NULL,
    output_micros_per_1k integer NOT NULL
);
