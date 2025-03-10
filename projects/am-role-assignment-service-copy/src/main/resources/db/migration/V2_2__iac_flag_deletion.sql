
delete from flag_config where flag_name='iac_1_0';

update flag_config set status='true' where flag_name='iac_1_1' and env='pr' and service_name='iac';

