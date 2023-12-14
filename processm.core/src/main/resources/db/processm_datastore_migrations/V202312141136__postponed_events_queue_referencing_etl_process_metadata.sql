ALTER TABLE postponed_events_queue ADD FOREIGN KEY (log_identity_id)
    REFERENCES etl_processes_metadata(id)
    ON DELETE CASCADE;