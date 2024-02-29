CREATE VIEW organizations_descendants AS
WITH RECURSIVE DescendantsCTE AS (SELECT id, parent_organization_id, ARRAY [id] AS path
                                  FROM organizations
                                  WHERE parent_organization_id IS NULL
                                  UNION ALL
                                  SELECT T.id, T.parent_organization_id, ARRAY [T.id] || DescendantsCTE.path AS path
                                  FROM organizations T
                                           JOIN DescendantsCTE
                                                ON T.parent_organization_id = DescendantsCTE.id)
SELECT sub_organization_id, super_organization_id
FROM (SELECT id AS sub_organization_id, UNNEST(path) AS super_organization_id
      FROM DescendantsCTE
      WHERE id != parent_organization_id) AS x
WHERE sub_organization_id != super_organization_id