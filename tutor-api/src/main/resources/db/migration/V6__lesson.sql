create table lesson (
    id                uuid         primary key,
    course_id         uuid         not null references course (id),
    topic_id          uuid         not null references topic (id),
    author_id         uuid         not null references app_user (id),
    title             varchar(200) not null,
    body_markdown     text         not null,
    status            varchar(32)  not null,
    published_version int,
    review_comment    varchar(2000),
    published_at      timestamptz,
    created_at        timestamptz  not null,
    updated_at        timestamptz  not null,
    version           bigint       not null,
    constraint lesson_status_check check (status in ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED'))
);

create index lesson_course_id_idx on lesson (course_id);
create index lesson_topic_id_idx on lesson (topic_id);
create index lesson_author_id_idx on lesson (author_id);

create table lesson_version (
    id            uuid         primary key,
    lesson_id     uuid         not null references lesson (id),
    version       int          not null,
    topic_id      uuid         not null references topic (id),
    title         varchar(200) not null,
    body_markdown text         not null,
    published_by  uuid         not null references app_user (id),
    created_at    timestamptz  not null,
    constraint lesson_version_number_key unique (lesson_id, version)
);

create index lesson_version_topic_id_idx on lesson_version (topic_id);
create index lesson_version_published_by_idx on lesson_version (published_by);
