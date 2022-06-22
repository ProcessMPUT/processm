ALTER TABLE automatic_etl_processes_relations
    ALTER COLUMN source_class_id TYPE integer USING (source_class_id::integer),
    ALTER COLUMN target_class_id TYPE integer USING (target_class_id::integer);

ALTER TABLE automatic_etl_processes_relations
    ADD CONSTRAINT automatic_etl_processes_relations_source_classes_fk FOREIGN KEY (source_class_id) REFERENCES classes(id),
    ADD CONSTRAINT automatic_etl_processes_relations_target_classes_fk FOREIGN KEY (target_class_id) REFERENCES classes(id);