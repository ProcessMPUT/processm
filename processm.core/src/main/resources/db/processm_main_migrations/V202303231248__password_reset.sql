create table emails
(
    id         uuid primary key default gen_random_uuid(),
    recipients text      not null,
    body       bytea     not null,
    sent       timestamp null
);

create table password_reset_requests
(
    id           uuid primary key                     default gen_random_uuid(),
    user_id      uuid references users (id) on delete cascade,
    not_before   timestamp                   not null default current_timestamp,
    not_after    timestamp                   not null default current_timestamp + '1h',
    email        uuid references emails (id) null,
    link_clicked timestamp                   null
);
