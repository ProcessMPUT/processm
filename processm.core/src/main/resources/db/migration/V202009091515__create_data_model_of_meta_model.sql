CREATE TABLE datamodels (
    id serial PRIMARY KEY,
    name text
);

CREATE TABLE classes (
    id serial PRIMARY KEY,
    datamodel_id integer NOT NULL,
    name text NOT NULL
);

CREATE TABLE relationships (
    id serial PRIMARY KEY,
    name text NOT NULL,
    source_class_id integer NOT NULL,
    target_class_id integer NOT NULL
);

CREATE TABLE attributes_names (
    id serial PRIMARY KEY,
    name text NOT NULL,
    class_id integer NOT NULL,
    type text NOT NULL
);

CREATE TABLE objects (
    id serial PRIMARY KEY,
    class_id integer NOT NULL
);

CREATE TABLE object_versions (
    id serial PRIMARY KEY,
    object_id integer NOT NULL,
    start_time timestamptz,
    end_time timestamptz
);

CREATE TABLE relations (
    id serial PRIMARY KEY,
    source_object_version_id integer NOT NULL,
    target_object_version_id integer NOT NULL,
    relationship_id integer NOT NULL,
    start_time timestamptz,
    end_time timestamptz
);

CREATE TABLE attributes_values (
    id serial PRIMARY KEY,
    object_version_id integer NOT NULL,
    attribute_name_id integer NOT NULL,
    value text
);

CREATE TABLE events_to_object_versions (
    event_id integer NOT NULL,
    object_version_id integer NOT NULL,
    label text
);

ALTER TABLE events_to_object_versions
    ADD CONSTRAINT events_to_object_versions_pk PRIMARY KEY (event_id, object_version_id);
ALTER TABLE classes
    ADD CONSTRAINT classes_datamodel_fk FOREIGN KEY (datamodel_id) REFERENCES datamodels(id);
ALTER TABLE relationships
    ADD CONSTRAINT relationships_sources_classes_fk FOREIGN KEY (source_class_id) REFERENCES classes(id);
ALTER TABLE relationships
    ADD CONSTRAINT relationships_target_classes_fk FOREIGN KEY (target_class_id) REFERENCES classes(id);
ALTER TABLE attributes_names
    ADD CONSTRAINT attributes_names_classes_fk FOREIGN KEY (class_id) REFERENCES classes(id);
ALTER TABLE objects
    ADD CONSTRAINT objects_classes_fk FOREIGN KEY (class_id) REFERENCES classes(id);
ALTER TABLE object_versions
    ADD CONSTRAINT object_versions_objects_fk FOREIGN KEY (object_id) REFERENCES objects(id);
ALTER TABLE relations
    ADD CONSTRAINT relations_source_object_versions_fk FOREIGN KEY (source_object_version_id) REFERENCES object_versions(id);
ALTER TABLE relations
    ADD CONSTRAINT relations_target_object_versions_fk FOREIGN KEY (target_object_version_id) REFERENCES object_versions(id);
ALTER TABLE relations
    ADD CONSTRAINT relations_relationships_fk FOREIGN KEY (relationship_id) REFERENCES relationships(id);
ALTER TABLE attributes_values
    ADD CONSTRAINT attributes_values_object_versions_fk FOREIGN KEY (object_version_id) REFERENCES object_versions(id);
ALTER TABLE attributes_values
    ADD CONSTRAINT attributes_values_attributes_names_fk FOREIGN KEY (attribute_name_id) REFERENCES attributes_names(id);

ALTER TABLE events_to_object_versions
    ADD CONSTRAINT events_to_object_versions_events_fk FOREIGN KEY (event_id) REFERENCES events(id);
ALTER TABLE events_to_object_versions
    ADD CONSTRAINT events_to_object_versions_object_versions_fk FOREIGN KEY (object_version_id) REFERENCES object_versions(id);



CREATE TABLE event_attribute_name (id serial PRIMARY KEY, name text, type text);

CREATE TABLE cases_to_logs (case_id serial REFERENCES "case" (id), log_id serial REFERENCES log (id), PRIMARY KEY (case_id, log_id));

CREATE TABLE classifiers (id serial PRIMARY KEY, log_id serial REFERENCES log (id), name text);

CREATE TABLE event (id serial PRIMARY KEY, activity_instance_id serial REFERENCES activity_instance (id), ordering serial, timestamp serial, lifecycle text, resource text);

CREATE TABLE activity_to_process (process_id serial REFERENCES process (id), activity_id serial REFERENCES activity (id), PRIMARY KEY (process_id, activity_id));

CREATE TABLE activity_instance_to_case (case_id serial REFERENCES "case" (id) NOT NULL, activity_instance_id serial REFERENCES activity_instance (id) NOT NULL, PRIMARY KEY (case_id, activity_instance_id));

CREATE TABLE activity_instance (id serial PRIMARY KEY, activity_id serial REFERENCES activity (id));

CREATE TABLE process (id serial PRIMARY KEY, name text);

CREATE TABLE "case" (id serial PRIMARY KEY, name text);

CREATE TABLE case_attribute_name (id serial PRIMARY KEY, name text, type text);

CREATE TABLE log_attribute_value (id serial PRIMARY KEY, log_attribute_name_id serial REFERENCES log_attribute_name (id), log_id serial REFERENCES log (id), value text, type text);

CREATE TABLE log (id serial PRIMARY KEY, process_id serial REFERENCES process (id), name text);

CREATE TABLE log_attribute_name (id serial PRIMARY KEY, name text, type text);

CREATE TABLE case_attribute_value (id serial PRIMARY KEY, case_id serial REFERENCES "case" (id), case_attribute_name_id serial REFERENCES case_attribute_name (id), value text, type text);

CREATE TABLE event_attribute_value (id serial PRIMARY KEY, event_id serial REFERENCES event (id), event_attribute_name_id serial REFERENCES event_attribute_name (id), value text, type text);

CREATE TABLE classifier_attributes (id serial PRIMARY KEY, classifier_id serial REFERENCES classifier (id), event_attribute_name_id serial REFERENCES event_attribute_name (id));

CREATE TABLE activity (id serial PRIMARY KEY, name text);