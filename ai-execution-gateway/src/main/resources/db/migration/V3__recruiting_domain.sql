CREATE TABLE candidate (
    id varchar(64) PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    requisition_id varchar(64) NOT NULL,
    full_name varchar(256) NOT NULL,
    email varchar(256) NOT NULL,
    status varchar(32) NOT NULL,
    skills text[] NOT NULL,
    years_experience integer NOT NULL
);

CREATE TABLE evidence (
    id varchar(64) PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    candidate_id varchar(64) NOT NULL REFERENCES candidate (id),
    snippet text NOT NULL
);

CREATE TABLE recommendation_draft (
    id uuid PRIMARY KEY,
    execution_id uuid NOT NULL REFERENCES execution (id),
    candidate_id varchar(64) NOT NULL REFERENCES candidate (id),
    recommendation varchar(16) NOT NULL,
    rationale text NOT NULL,
    cited_evidence_ids text[] NOT NULL
);

CREATE TABLE transition_request (
    id uuid PRIMARY KEY,
    tenant_id varchar(64) NOT NULL REFERENCES tenant (id),
    candidate_id varchar(64) NOT NULL REFERENCES candidate (id),
    from_status varchar(32) NOT NULL,
    to_status varchar(32) NOT NULL,
    draft_id uuid,
    execution_id uuid NOT NULL REFERENCES execution (id),
    status varchar(16) NOT NULL,
    decided_by varchar(64),
    decided_at timestamptz
);
