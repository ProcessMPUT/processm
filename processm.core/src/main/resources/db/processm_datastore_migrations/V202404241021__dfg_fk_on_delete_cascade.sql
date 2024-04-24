alter table directly_follows_graph_arc
    drop constraint fk_dfg_id;

alter table directly_follows_graph_arc
    add constraint fk_dfg_id
        foreign key (dfg_id) references directly_follows_graph
            on update cascade on delete cascade;

alter table directly_follows_graph_start_activities
    drop constraint fk_dfg_id;

alter table directly_follows_graph_start_activities
    add constraint fk_dfg_id
        foreign key (dfg_id) references directly_follows_graph
            on update cascade on delete cascade;

alter table directly_follows_graph_end_activities
    drop constraint fk_dfg_id;

alter table directly_follows_graph_end_activities
    add constraint fk_dfg_id
        foreign key (dfg_id) references directly_follows_graph
            on update cascade on delete cascade;

