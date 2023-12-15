ALTER TABLE relationships
    ADD COLUMN data_model_id integer NOT NULL REFERENCES data_models(id);