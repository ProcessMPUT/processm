CREATE TABLE logs
(
    id                SERIAL PRIMARY KEY,
    features          TEXT,
    "concept:name"    TEXT,
    "identity:id"     TEXT,
    "lifecycle:model" TEXT
);