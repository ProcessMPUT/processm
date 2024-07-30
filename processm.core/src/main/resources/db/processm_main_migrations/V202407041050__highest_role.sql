-- Given two role names returns the higher. If both arguments are NULL, returns NULL
CREATE OR REPLACE FUNCTION higher_role(TEXT, TEXT) RETURNS TEXT
AS
$$
    -- according to https://www.postgresql.org/docs/16/plpgsql-control-structures.html case is evaluated top to bottom
SELECT CASE
           WHEN $1 is NULL THEN $2
           WHEN $1 = 'owner' OR $2 = 'owner' THEN 'owner'
           WHEN $1 = 'writer' OR $2 = 'writer' THEN 'writer'
           WHEN $1 = 'reader' OR $2 = 'reader' THEN 'reader'
           WHEN $1 = 'none' OR $2 = 'none' THEN 'none'
           END CASE;
$$ LANGUAGE SQL;

-- An aggregate function aggregating multiple role names into the highest value
CREATE OR REPLACE AGGREGATE highest_role(TEXT) (SFUNC = higher_role, STYPE =TEXT);