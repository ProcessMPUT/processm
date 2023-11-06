drop view objects_in_trace;

-- Initially objects_in_traces (formerly objects_in_trace) used a custom datatype remote_object_identifier
-- It seems that checking whether one array of such objects is a subarray of another is much more expensive
-- than the same operation performed on arrays of strings
-- On the other hand, since class_id is an integer, it is unambiguous to construct joint_id in such a way

create view objects_in_traces as
select trace_id, array_agg(joint_id) as objects
from (select distinct events.trace_id, (objects_in_events.class_id || '_' || objects_in_events.object_id) as joint_id
      from objects_in_events,
           events
      where events.id = objects_in_events.event_id) x
group by trace_id
;