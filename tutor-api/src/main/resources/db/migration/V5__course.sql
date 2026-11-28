create table course (
    id          uuid          primary key,
    school_id   uuid          not null references school (id),
    teacher_id  uuid          not null references app_user (id),
    title       varchar(200)  not null,
    subject     varchar(100)  not null,
    grade_level int           not null,
    description varchar(2000),
    status      varchar(32)   not null,
    created_at  timestamptz   not null,
    updated_at  timestamptz   not null,
    constraint course_grade_level_check check (grade_level between 1 and 12),
    constraint course_status_check check (status in ('ACTIVE', 'ARCHIVED'))
);

create index course_school_id_idx on course (school_id);
create index course_teacher_id_idx on course (teacher_id);

create table topic (
    id         uuid         primary key,
    course_id  uuid         not null references course (id),
    name       varchar(200) not null,
    position   int          not null,
    created_at timestamptz  not null,
    constraint topic_course_name_key unique (course_id, name)
);

create table enrollment (
    course_id  uuid        not null references course (id),
    student_id uuid        not null references app_user (id),
    created_at timestamptz not null,
    primary key (course_id, student_id)
);

create index enrollment_student_id_idx on enrollment (student_id);
