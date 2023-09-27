create table single_value_metadata
(
    id           uuid             NOT NULL PRIMARY KEY DEFAULT gen_random_uuid(),
    urn          text             NOT NULL UNIQUE,
    double_value double precision NULL
);