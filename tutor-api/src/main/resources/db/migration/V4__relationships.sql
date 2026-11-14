create table guardian_link (
    id         uuid        primary key,
    parent_id  uuid        not null references app_user (id),
    student_id uuid        not null references app_user (id),
    created_at timestamptz not null,
    constraint guardian_link_parent_student_key unique (parent_id, student_id)
);

create index guardian_link_student_id_idx on guardian_link (student_id);

create table tutor_assignment (
    id         uuid        primary key,
    tutor_id   uuid        not null references app_user (id),
    student_id uuid        not null references app_user (id),
    created_at timestamptz not null,
    constraint tutor_assignment_tutor_student_key unique (tutor_id, student_id)
);

create index tutor_assignment_student_id_idx on tutor_assignment (student_id);
