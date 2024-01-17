ALTER TABLE users
    ADD COLUMN first_name text,
    ADD COLUMN last_name text,
    ADD COLUMN email_address text UNIQUE NOT NULL,
    ADD COLUMN password text NOT NULL,
    ADD COLUMN locale text,
    ADD CONSTRAINT username_unique UNIQUE (username),
    ALTER COLUMN username DROP NOT NULL;