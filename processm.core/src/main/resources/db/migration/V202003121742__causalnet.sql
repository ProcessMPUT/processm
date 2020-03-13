create table causalnetmodel
(
	id serial not null
		constraint causalnetmodel_pkey
			primary key,
	start integer,
	"end" integer
);

create table causalnetnode
(
	id serial not null
		constraint causalnetnode_pkey
			primary key,
	activity varchar(100) not null,
	instance varchar(100) not null,
	special boolean not null,
	model integer not null
		constraint fk_causalnetnode_model_id
			references causalnetmodel
				on update restrict on delete cascade,
	constraint causalnetnode_activity_instance_special_model_unique
		unique (activity, instance, special, model)
);

alter table causalnetmodel
	add constraint fk_causalnetmodel_start_id
		foreign key (start) references causalnetnode
			on update restrict on delete cascade;

alter table causalnetmodel
	add constraint fk_causalnetmodel_end_id
		foreign key ("end") references causalnetnode
			on update restrict on delete cascade;

create table causalnetdependency
(
	id serial not null
		constraint causalnetdependency_pkey
			primary key,
	source integer not null
		constraint fk_causalnetdependency_source_id
			references causalnetnode
				on update restrict on delete cascade,
	target integer not null
		constraint fk_causalnetdependency_target_id
			references causalnetnode
				on update restrict on delete cascade,
	model integer not null
		constraint fk_causalnetdependency_model_id
			references causalnetmodel
				on update restrict on delete cascade,
	constraint causalnetdependency_source_target_unique
		unique (source, target)
);

create table causalnetbinding
(
	id uuid not null
		constraint causalnetbinding_pkey
			primary key,
	isjoin boolean not null,
	model integer not null
		constraint fk_causalnetbinding_model_id
			references causalnetmodel
				on update restrict on delete cascade
);

create table causalnetdependencybindings
(
	dependency integer not null
		constraint fk_causalnetdependencybindings_dependency_id
			references causalnetdependency
				on update restrict on delete cascade,
	binding uuid not null
		constraint fk_causalnetdependencybindings_binding_id
			references causalnetbinding
				on update restrict on delete cascade,
	constraint pk_causalnetdependencybindings
		primary key (dependency, binding)
);