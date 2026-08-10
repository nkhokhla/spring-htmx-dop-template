create table if not exists note
(
    id         text primary key,
    text       text not null check (length(text) <= 280),
    created_at text not null
);
