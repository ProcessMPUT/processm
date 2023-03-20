create table petrinets
(
    id uuid not null primary key
);

create table petrinets_transitions
(
    id         uuid not null primary key,
    petrinet   uuid references petrinets(id),
    name       text not null,
    is_silent  bool not null,
    in_places  text not null,
    out_places text not null
);

create table petrinets_places
(
    id       uuid not null primary key,
    petrinet uuid references petrinets(id),
    initial_marking integer null,
    final_marking integer null
);