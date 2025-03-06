-- enable all_wa_services_case_allocator_1_0 flag in Prod for: DTSAM-366/DTSAM-330
update flag_config set status='true' where flag_name='all_wa_services_case_allocator_1_0' and env in ('demo', 'aat', 'perftest', 'ithc', 'prod');
