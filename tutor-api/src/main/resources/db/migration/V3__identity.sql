create table school (
    id         uuid primary key,
    name       varchar(200) not null,
    created_at timestamptz  not null
);

insert into school (id, name, created_at)
values ('00000000-0000-0000-0000-000000000001', 'Riverside Academy', now());

create table app_user (
    id           uuid primary key,
    keycloak_id  varchar(64)  not null,
    username     varchar(100) not null,
    display_name varchar(200) not null,
    email        varchar(320),
    role         varchar(32)  not null,
    school_id    uuid         not null references school (id),
    created_at   timestamptz  not null,
    last_seen_at timestamptz  not null,
    constraint app_user_keycloak_id_key unique (keycloak_id),
    constraint app_user_username_key unique (username),
    constraint app_user_role_check check (role in ('ADMIN', 'TEACHER', 'TUTOR', 'STUDENT', 'PARENT'))
);

create index app_user_school_id_idx on app_user (school_id);
