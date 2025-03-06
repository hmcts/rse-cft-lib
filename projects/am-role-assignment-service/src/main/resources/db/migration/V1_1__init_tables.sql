

CREATE TABLE actor_cache_control(
	actor_id text NOT NULL,
	etag int4 NOT NULL,
	json_response jsonb NOT NULL,
	CONSTRAINT actor_cache_control_pkey PRIMARY KEY (actor_id)
);


CREATE TABLE role_assignment(
	id uuid NOT NULL,
	actor_id_type text NOT NULL,
	actor_id text NOT NULL,
	role_type text NOT NULL,
	role_name text NOT NULL,
	classification text NOT NULL,
	grant_type text NOT NULL,
	role_category text NULL,
	read_only bool NOT NULL,
	begin_time timestamp NULL,
	end_time timestamp NULL,
	"attributes" jsonb NOT NULL,
	created timestamp NOT NULL,
	authorisations _text NULL,
	CONSTRAINT role_assignment_pkey PRIMARY KEY (id)
);



CREATE TABLE role_assignment_history(
	id uuid NOT NULL,
	request_id uuid NOT NULL,
	actor_id_type text NOT NULL,
	actor_id text NOT NULL,
	role_type text NOT NULL,
	role_name text NOT NULL,
	classification text NOT NULL,
	grant_type text NOT NULL,
	role_category text NULL,
	read_only bool NOT NULL,
	begin_time timestamp NULL,
	end_time timestamp NULL,
	status text NOT NULL,
	reference text NULL,
	process text NULL,
	"attributes" jsonb NOT NULL,
	notes jsonb NULL,
	log text NULL,
	status_sequence int4 NOT NULL,
	created timestamp NOT NULL,
	authorisations _text NULL,
	CONSTRAINT pk_role_assignment_history PRIMARY KEY (id, request_id, status)
);



CREATE TABLE role_assignment_request(
	id uuid NOT NULL,
	correlation_id text NOT NULL,
	client_id text NOT NULL,
	authenticated_user_id text NOT NULL,
	assigner_id text NOT NULL,
	request_type text NOT NULL,
	status text NOT NULL,
	process text NULL,
	reference text NULL,
	replace_existing bool NULL,
	log text NULL,
	role_assignment_id uuid NULL,
	created timestamp NOT NULL,
	CONSTRAINT role_assignment_request_pkey PRIMARY KEY (id)
);


ALTER TABLE role_assignment_history ADD CONSTRAINT fk_role_assignment_history_role_assignment_request FOREIGN KEY (request_id) REFERENCES role_assignment_request(id);

CREATE INDEX idx_process_reference ON role_assignment_history USING btree (process, reference);
CREATE INDEX idx_actor_id ON role_assignment USING btree (actor_id);




