create table ai_call_log (
    id                uuid primary key,
    feature           varchar(32)  not null,
    prompt_version    varchar(64),
    provider          varchar(32)  not null,
    model             varchar(64)  not null,
    user_id           uuid,
    prompt_tokens     int,
    completion_tokens int,
    latency_ms        int          not null,
    outcome           varchar(32)  not null,
    error             varchar(500),
    created_at        timestamptz  not null
);

create index ai_call_log_feature_created_at_idx on ai_call_log (feature, created_at);
