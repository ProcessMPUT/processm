DROP INDEX events_attributes_parent_id_index;
ALTER TABLE events_attributes DROP parent_id;
ALTER TABLE events_attributes DROP in_list_attr;

DROP INDEX traces_attributes_parent_id_index;
ALTER TABLE traces_attributes DROP parent_id;
ALTER TABLE traces_attributes DROP in_list_attr;

DROP INDEX logs_attributes_parent_id_index;
ALTER TABLE logs_attributes DROP parent_id;
ALTER TABLE logs_attributes DROP in_list_attr;

DROP INDEX globals_parent_id_index;
ALTER TABLE globals DROP parent_id;
ALTER TABLE globals DROP in_list_attr;

CREATE OR REPLACE FUNCTION get_log_attribute(vlog_id int, vkey text, vexpected_type attribute_type, db_type anynonarray)
    RETURNS anynonarray
    LANGUAGE plpgsql
    STABLE
    LEAKPROOF
    PARALLEL SAFE
AS
$$
DECLARE
vtype         attribute_type;
    vstring_value text;
    vuuid_value   uuid;
    vdate_value   timestamptz;
    vint_value    bigint;
    vbool_value   boolean;
    vreal_value   double precision;
BEGIN
SELECT type, string_value, uuid_value, date_value, int_value, bool_value, real_value
INTO vtype, vstring_value, vuuid_value, vdate_value, vint_value, vbool_value, vreal_value
FROM logs_attributes
WHERE log_id = vlog_id
  AND key = vkey;

IF vtype IS NULL THEN
        RETURN NULL;
END IF;

    IF vexpected_type != 'any'::attribute_type -- type enforcement is on
        AND vtype != vexpected_type -- check if the actual type matches the expected type
        AND vtype != 'int'::attribute_type -- exception for seamless casting of int to float
        AND vexpected_type != 'float'::attribute_type
    THEN
        RAISE EXCEPTION 'Expected type % for attribute %, % found.', vexpected_type, vkey, vtype;
END IF;

    IF vtype = 'string' THEN RETURN vstring_value; END IF;
    IF vtype = 'id' THEN RETURN vuuid_value; END IF;
    IF vtype = 'date' THEN RETURN vdate_value; END IF;
    IF vtype = 'int' THEN RETURN vint_value::double precision; END IF;
    IF vtype = 'boolean' THEN RETURN vbool_value; END IF;
    IF vtype = 'float' THEN RETURN vreal_value; END IF;

    RAISE EXCEPTION 'Unknown type %.', vtype;
END ;
$$;

CREATE OR REPLACE FUNCTION get_trace_attribute(vtrace_id bigint, vkey text, vexpected_type attribute_type,
                                               db_type anynonarray)
    RETURNS anynonarray
    LANGUAGE plpgsql
    STABLE
    LEAKPROOF
    PARALLEL SAFE
AS
$$
DECLARE
vtype         attribute_type;
    vstring_value text;
    vuuid_value   uuid;
    vdate_value   timestamptz;
    vint_value    bigint;
    vbool_value   boolean;
    vreal_value   double precision;
BEGIN
SELECT type, string_value, uuid_value, date_value, int_value, bool_value, real_value
INTO vtype, vstring_value, vuuid_value, vdate_value, vint_value, vbool_value, vreal_value
FROM traces_attributes
WHERE trace_id = vtrace_id
  AND key = vkey;

IF vtype IS NULL THEN
        RETURN NULL;
END IF;

    IF vexpected_type != 'any'::attribute_type -- type enforcement is on
        AND vtype != vexpected_type -- check if the actual type matches the expected type
        AND vtype != 'int'::attribute_type -- exception for seamless casting of int to float
        AND vexpected_type != 'float'::attribute_type
    THEN
        RAISE EXCEPTION 'Expected type % for attribute %, % found.', vexpected_type, vkey, vtype;
END IF;

    IF vtype = 'string' THEN RETURN vstring_value; END IF;
    IF vtype = 'id' THEN RETURN vuuid_value; END IF;
    IF vtype = 'date' THEN RETURN vdate_value; END IF;
    IF vtype = 'int' THEN RETURN vint_value::double precision; END IF;
    IF vtype = 'boolean' THEN RETURN vbool_value; END IF;
    IF vtype = 'float' THEN RETURN vreal_value; END IF;

    RAISE EXCEPTION 'Unknown type %.', vtype;
END ;
$$;

CREATE OR REPLACE FUNCTION get_event_attribute(vevent_id bigint, vkey text, vexpected_type attribute_type,
                                               db_type anynonarray)
    RETURNS anynonarray
    LANGUAGE plpgsql
    STABLE
    LEAKPROOF
    PARALLEL SAFE
AS
$$
DECLARE
vtype         attribute_type;
    vstring_value text;
    vuuid_value   uuid;
    vdate_value   timestamptz;
    vint_value    bigint;
    vbool_value   boolean;
    vreal_value   double precision;
BEGIN
SELECT type, string_value, uuid_value, date_value, int_value, bool_value, real_value
INTO vtype, vstring_value, vuuid_value, vdate_value, vint_value, vbool_value, vreal_value
FROM events_attributes
WHERE event_id = vevent_id
  AND key = vkey;

IF vtype IS NULL THEN
        RETURN NULL;
END IF;

    IF vexpected_type != 'any'::attribute_type -- type enforcement is on
        AND vtype != vexpected_type -- check if the actual type matches the expected type
        AND vtype != 'int'::attribute_type -- exception for seamless casting of int to float
        AND vexpected_type != 'float'::attribute_type
    THEN
        RAISE EXCEPTION 'Expected type % for attribute %, % found.', vexpected_type, vkey, vtype;
END IF;

    IF vtype = 'string' THEN RETURN vstring_value; END IF;
    IF vtype = 'id' THEN RETURN vuuid_value; END IF;
    IF vtype = 'date' THEN RETURN vdate_value; END IF;
    IF vtype = 'int' THEN RETURN vint_value::double precision; END IF;
    IF vtype = 'boolean' THEN RETURN vbool_value; END IF;
    IF vtype = 'float' THEN RETURN vreal_value; END IF;

    RAISE EXCEPTION 'Unknown type %.', vtype;
END ;
$$;