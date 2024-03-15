CREATE TABLE bpmn
(
    id  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    -- there exists the xml type, but using it is more cumbersome (Exposed) yet I see no benefits - hence text
    xml text not null
);