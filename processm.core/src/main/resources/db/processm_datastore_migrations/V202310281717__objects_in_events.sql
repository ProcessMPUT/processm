create view objects_in_events as
    select b.event_id, b.string_value as object_id, c.int_value as class_id
    from events_attributes b, events_attributes c
    where b.event_id=c.event_id and b.key='org:resource' and c.key='processm:internal:classId';
