CREATE INDEX logs_xes_version ON logs ("xes:version") WHERE "xes:version" IS NOT NULL;
CREATE INDEX logs_xes_features ON logs ("xes:features") WHERE "xes:features" IS NOT NULL;
CREATE INDEX logs_concept_name ON logs ("concept:name") WHERE "concept:name" IS NOT NULL;
CREATE INDEX logs_lifecycle_model ON logs ("lifecycle:model") WHERE "lifecycle:model" IS NOT NULL;

CREATE INDEX logs_attributes_key ON logs_attributes ("key") WHERE "key" IS NOT NULL;

CREATE INDEX traces_concept_name ON traces ("concept:name") WHERE "concept:name" IS NOT NULL;
CREATE INDEX traces_cost_currency ON traces ("cost:currency") WHERE "cost:currency" IS NOT NULL;
CREATE INDEX traces_cost_total ON traces ("cost:total") WHERE "cost:total" IS NOT NULL;
CREATE INDEX traces_identity_id ON traces ("identity:id") WHERE "identity:id" IS NOT NULL;

CREATE INDEX traces_attributes_key ON traces_attributes ("key") WHERE "key" IS NOT NULL;

CREATE INDEX events_concept_name ON events ("concept:name") WHERE "concept:name" IS NOT NULL;
CREATE INDEX events_concept_instance ON events ("concept:instance") WHERE "concept:instance" IS NOT NULL;
CREATE INDEX events_cost_total ON events ("cost:total") WHERE "cost:total" IS NOT NULL;
CREATE INDEX events_cost_currency ON events ("cost:currency") WHERE "cost:currency" IS NOT NULL;
CREATE INDEX events_identity_id ON events ("identity:id") WHERE "identity:id" IS NOT NULL;
CREATE INDEX events_lifecycle_transition ON events ("lifecycle:transition") WHERE "lifecycle:transition" IS NOT NULL;
CREATE INDEX events_lifecycle_state ON events ("lifecycle:state") WHERE "lifecycle:state" IS NOT NULL;
CREATE INDEX events_org_resource ON events ("org:resource") WHERE "org:resource" IS NOT NULL;
CREATE INDEX events_org_role ON events ("org:role") WHERE "org:role" IS NOT NULL;
CREATE INDEX events_org_group ON events ("org:group") WHERE "org:group" IS NOT NULL;
CREATE INDEX events_time_timestamp ON events ("time:timestamp") WHERE "time:timestamp" IS NOT NULL;

CREATE INDEX events_attributes_key ON events_attributes ("key") WHERE "key" IS NOT NULL;
