-- The column is nullable as not to break non-ETL journals
alter table events add column version bigint;
create sequence xes_version as bigint start 1;