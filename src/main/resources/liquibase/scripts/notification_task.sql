-- liquibase formatted sql

-- changeset d1m_k0:1
create table notification_task (
    id serial primary key,
    chatId bigint,
    message text,
    date_and_time timestamp
)