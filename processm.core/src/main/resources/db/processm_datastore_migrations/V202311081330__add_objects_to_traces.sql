alter table traces
    add column objects jsonb default '{}'::jsonb;

drop view objects_in_traces;