create table app_user (
    id         bigserial primary key,
    google_sub text        not null unique,
    email      text        not null,
    name       text,
    created_at timestamptz not null default now()
);
