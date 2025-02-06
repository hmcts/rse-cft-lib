-- enable disposer1_0 flag in Prod for: DTSAM-85
update flag_config set status='true' where flag_name='disposer_1_0' and env in ('demo', 'aat', 'perftest', 'ithc', 'prod');
