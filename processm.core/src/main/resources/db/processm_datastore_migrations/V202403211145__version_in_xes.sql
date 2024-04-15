-- The column is nullable as not to break non-ETL journals
alter table events
    add column version bigint not null default 1;
create sequence xes_version as bigint start 1;