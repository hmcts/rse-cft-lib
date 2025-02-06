-- enable sscs_case_allocator_1_0 flag in Prod for: DTSAM-331
update flag_config set status='true' where flag_name='sscs_case_allocator_1_0' and env in ('demo', 'aat', 'perftest', 'ithc', 'prod');
