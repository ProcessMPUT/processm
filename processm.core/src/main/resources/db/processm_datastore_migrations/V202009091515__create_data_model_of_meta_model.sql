CREATE TABLE data_models (
    id serial PRIMARY KEY,
    name text,
    version_date timestamp NOT NULL DEFAULT NOW()
);

CREATE TABLE classes (
    id serial PRIMARY KEY,
    data_model_id integer NOT NULL,
    name text NOT NULL
);

CREATE TABLE relationships (
    id serial PRIMARY KEY,
    name text NOT NULL,
    source_class_id integer NOT NULL,
    target_class_id integer NOT NULL,
    referencing_attribute_name_id integer NOT NULL
);

CREATE TABLE attributes_names (
    id serial PRIMARY KEY,
    name text NOT NULL,
    is_referencing_attribute boolean NOT NULL,
    class_id integer NOT NULL,
    type text NOT NULL
);

CREATE TABLE object_versions (
    id serial PRIMARY KEY,
    previous_object_version_id integer,
    class_id integer NOT NULL,
    object_id text NOT NULL,
    causing_event_type text,
    additional_data text,
    start_time bigint,
    end_time bigint
);

CREATE TABLE relations (
    id serial PRIMARY KEY,
    source_object_version_id integer NOT NULL,
    target_object_version_id integer NOT NULL,
    relationship_id integer NOT NULL,
    start_time bigint,
    end_time bigint
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
    ADD CONSTRAINT classes_data_model_fk FOREIGN KEY (data_model_id) REFERENCES data_models(id);
ALTER TABLE relationships
    ADD CONSTRAINT relationships_sources_classes_fk FOREIGN KEY (source_class_id) REFERENCES classes(id);
ALTER TABLE relationships
    ADD CONSTRAINT relationships_target_classes_fk FOREIGN KEY (target_class_id) REFERENCES classes(id);
ALTER TABLE relationships
    ADD CONSTRAINT relationships_attributes_names_fk FOREIGN KEY (referencing_attribute_name_id) REFERENCES attributes_names(id);;
ALTER TABLE attributes_names
    ADD CONSTRAINT attributes_names_classes_fk FOREIGN KEY (class_id) REFERENCES classes(id);
ALTER TABLE object_versions
    ADD CONSTRAINT object_versions_object_versions_fk FOREIGN KEY (previous_object_version_id) REFERENCES object_versions(id);
ALTER TABLE object_versions
    ADD CONSTRAINT object_versions_classes_fk FOREIGN KEY (class_id) REFERENCES classes(id);
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
    ADD CONSTRAINT events_to_object_versions_object_versions_fk FOREIGN KEY (object_version_id) REFERENCES object_versions(id);