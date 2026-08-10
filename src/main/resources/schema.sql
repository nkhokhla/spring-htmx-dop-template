create table if not exists note
(
    id         char(36)     not null primary key,
    text       varchar(280) not null,
    created_at datetime(6)  not null
);
