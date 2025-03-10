--Add new index with upper case process and reference combination.
CREATE INDEX CONCURRENTLY idx_process_reference_upper ON role_assignment_history USING btree (upper(process), upper(reference));
