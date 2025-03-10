-- enable sscs_challenged_1_0 flag in Demo, AAT, Perftest for: DTSAM-236
update flag_config set status='true' where flag_name='sscs_challenged_1_0' and env in ('demo', 'aat', 'perftest');
