ALTER TABLE users
    ADD COLUMN password text NOT NULL,
    ADD COLUMN locale varchar(5);