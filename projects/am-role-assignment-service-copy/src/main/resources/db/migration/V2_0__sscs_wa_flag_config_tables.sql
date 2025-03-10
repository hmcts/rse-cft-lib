-- insert iac base flag into flag_config table
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'local', 'sscs', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'pr', 'sscs', 'true');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'aat', 'sscs', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'demo', 'sscs', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'perftest', 'sscs', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'ithc', 'sscs', 'false');
INSERT INTO flag_config (flag_name, env, service_name, status) VALUES ('sscs_wa_1_0', 'prod', 'sscs', 'false');
