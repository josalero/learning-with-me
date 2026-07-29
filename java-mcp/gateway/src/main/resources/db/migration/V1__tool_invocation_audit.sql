-- Iteration 3: tool invocation audit (no argument bodies / no PII payloads).
create table tool_invocation_audit (
    id                  bigserial primary key,
    invocation_id       varchar(64)  not null,
    correlation_id      varchar(64)  not null,
    session_id          varchar(64),
    tool_name           varchar(128) not null,
    connection_name     varchar(128) not null,
    connector_type      varchar(64)  not null,
    subject             varchar(256) not null,
    tenant_id           varchar(128) not null,
    authentication_type varchar(64)  not null,
    authorization_decision varchar(32) not null,
    approval_outcome    varchar(32),
    argument_hash       varchar(128) not null,
    started_at          timestamptz  not null,
    completed_at        timestamptz  not null,
    result_category     varchar(32)  not null,
    removed_field_count integer      not null default 0,
    error_reference     varchar(256)
);

create index idx_tool_invocation_audit_tool_started
    on tool_invocation_audit (tool_name, started_at desc);

create index idx_tool_invocation_audit_tenant_started
    on tool_invocation_audit (tenant_id, started_at desc);
