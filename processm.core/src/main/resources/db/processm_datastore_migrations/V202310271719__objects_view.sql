create view objects_in_trace as
    select trace_id, array_agg(distinct ROW(
        (select int_value from events_attributes where events.id=event_id and key='processm:internal:classId'),
        (select string_value from events_attributes where events.id=event_id and key='org:resource'))::remote_object_identifier
                     ) as objects
    from events
    group by trace_id;