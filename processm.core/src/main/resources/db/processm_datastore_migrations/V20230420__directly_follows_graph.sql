CREATE EXTENSION pgcrypto;

CREATE TABLE directly_follows_graph
(
    id uuid PRIMARY KEY DEFAULT gen_random_uuid()
);

CREATE TABLE directly_follows_graph_arc
(
    id          bigserial PRIMARY KEY,
    dfg_id      uuid NOT NULL,
    predecessor TEXT NOT NULL,
    successor   TEXT NOT NULL,
    cardinality INT  NOT NULL DEFAULT 1,
    CONSTRAINT fk_dfg_id FOREIGN KEY (dfg_id) REFERENCES directly_follows_graph (id)
);

CREATE TABLE directly_follows_graph_start_activities
(
    id          bigserial PRIMARY KEY,
    dfg_id      uuid NOT NULL,
    name        TEXT NOT NULL,
    cardinality INT  NOT NULL DEFAULT 1,
    CONSTRAINT fk_dfg_id FOREIGN KEY (dfg_id) REFERENCES directly_follows_graph
);

CREATE TABLE directly_follows_graph_end_activities
(
    id          bigserial PRIMARY KEY,
    dfg_id      uuid NOT NULL,
    name        TEXT NOT NULL,
    cardinality INT  NOT NULL DEFAULT 1,
    CONSTRAINT fk_dfg_id FOREIGN KEY (dfg_id) REFERENCES directly_follows_graph
);

CREATE INDEX directly_follows_graph_arc_dfg_id ON directly_follows_graph_arc (dfg_id);
CREATE INDEX directly_follows_graph_start_activities_dfg_id ON directly_follows_graph_start_activities (dfg_id);
CREATE INDEX directly_follows_graph_end_activities_dfg_id ON directly_follows_graph_end_activities (dfg_id);

