CREATE TABLE classifiers
(
    id     SERIAL PRIMARY KEY,
    log_id INTEGER NOT NULL,
    scope  TEXT,
    name   TEXT,
    keys   TEXT
);

CREATE INDEX classifiers_log_id_index ON classifiers (log_id);
