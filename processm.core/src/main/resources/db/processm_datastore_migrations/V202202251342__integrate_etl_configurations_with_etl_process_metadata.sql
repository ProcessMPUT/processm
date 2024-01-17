alter table etl_processes_metadata add last_execution_time timestamp with time zone;

/*
 There should be some code to copy data from etl_configuration to etl_processes_metadata.
 It seems that it'd be useless, as there's no system that would make use of it.
 */

alter table etl_configurations drop column last_execution_time;
alter table etl_configurations drop column name;
alter table etl_configurations drop column data_connector;
alter table etl_configurations add column metadata uuid not null references etl_processes_metadata(id) ON DELETE CASCADE;
