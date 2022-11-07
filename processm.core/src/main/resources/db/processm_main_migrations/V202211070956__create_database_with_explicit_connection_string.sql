CREATE OR REPLACE FUNCTION create_database(database text, connection text)
    RETURNS BOOLEAN
    LANGUAGE plpgsql
    STRICT
AS
$$
BEGIN
    IF EXISTS (SELECT FROM pg_database WHERE datname = database) THEN
        RAISE NOTICE 'Database already exists';  -- optional
    ELSE
        PERFORM dblink_exec(connection, concat('CREATE DATABASE "', database, '"'));
    END IF;

    RETURN EXISTS(SELECT 1 FROM pg_database WHERE datname = database);
END
$$;

