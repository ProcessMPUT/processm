UPDATE workspace_components
SET data='[{"modelId": "' || data || '", "alignmentKPIId": ""}]'
WHERE type IN ('causalNet', 'directlyFollowsGraph', 'petriNet');

ALTER TABLE workspace_components
    DROP COLUMN IF EXISTS model_id,
    DROP COLUMN IF EXISTS model_type;
