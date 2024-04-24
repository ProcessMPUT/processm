CREATE TABLE extensions
(
    id     SERIAL PRIMARY KEY,
    log_id INTEGER NOT NULL,
    name   TEXT,
    prefix TEXT,
    uri    TEXT
);

CREATE INDEX extensions_log_id_index ON extensions (log_id);
