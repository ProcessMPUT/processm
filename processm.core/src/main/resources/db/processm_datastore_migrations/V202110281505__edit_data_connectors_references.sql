ALTER TABLE data_connectors
    ADD COLUMN data_model_id integer NULL;

ALTER TABLE data_connectors
    ADD CONSTRAINT data_connectors_data_model_fk FOREIGN KEY (data_model_id) REFERENCES data_models(id);