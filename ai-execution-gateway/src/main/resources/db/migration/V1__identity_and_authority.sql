CREATE TABLE tenant (
    id varchar(64) PRIMARY KEY
);

CREATE TABLE principal (
    id varchar(64) PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    kind varchar(16) NOT NULL,
    display_label varchar(128) NOT NULL,
    scopes text[] NOT NULL
);

CREATE TABLE api_key (
    id uuid PRIMARY KEY,
    principal_id varchar(64) NOT NULL REFERENCES principal (id),
    key_hash varchar(64) NOT NULL UNIQUE,
    created_at timestamptz NOT NULL,
    revoked_at timestamptz
);

CREATE TABLE agent (
    id varchar(64) PRIMARY KEY,
    description varchar(512) NOT NULL
);

CREATE TABLE tool_policy (
    agent_id varchar(64) NOT NULL REFERENCES agent (id),
    tool_name varchar(128) NOT NULL,
    PRIMARY KEY (agent_id, tool_name)
);

CREATE TABLE delegation_grant (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    principal_id varchar(64) NOT NULL REFERENCES principal (id),
    agent_id varchar(64) NOT NULL REFERENCES agent (id),
    max_tool_calls integer NOT NULL,
    max_model_calls integer NOT NULL,
    max_tokens integer NOT NULL,
    max_cost_micros bigint NOT NULL,
    expires_at timestamptz
);
