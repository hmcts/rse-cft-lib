-- create table
CREATE TABLE flag_config(
	id bigint not null,
  flag_name text NOT NULL,
	env text NOT NULL,
	service_name text NOT NULL,
	status bool NOT NULL,
	CONSTRAINT flag_config_pkey PRIMARY KEY (id)
);
-- create sequence
create sequence ID_SEQ;
-- add sequence to table
ALTER TABLE flag_config ALTER COLUMN id
SET DEFAULT nextval('ID_SEQ');

-- insert iac base flag into flag_config table
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'pr', 'iac', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'aat', 'iac', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'demo', 'iac', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'perftest', 'iac', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'ithc', 'iac', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_0', 'prod', 'iac', 'true');

INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'pr', 'iac', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'aat', 'iac', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'demo', 'iac', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'perftest', 'iac', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'ithc', 'iac', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('iac_1_1', 'prod', 'iac', 'false');



