UPDATE workspace_components
SET data='[{"modelId": "' || data || '", "alignmentKPIId": ""}]'
WHERE type IN ('causalNet', 'directlyFollowsGraph', 'petriNet');

ALTER TABLE workspace_components
    DROP COLUMN model_id,
    DROP COLUMN model_type;
