create table if not exists note
(
    id         uuid primary key,
    text       varchar(280)             not null,
    created_at timestamp with time zone not null
);
