ALTER TABLE automatic_etl_processes_relations
    DROP COLUMN source_class_id,
    DROP COLUMN target_class_id,
    ADD COLUMN relationship_id int NOT NULL REFERENCES relationships (id) ON DELETE RESTRICT
;