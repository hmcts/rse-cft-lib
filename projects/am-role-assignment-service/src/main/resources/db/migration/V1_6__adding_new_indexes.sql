--New Gin index on the attribute jsonb for any containment operator.
CREATE INDEX CONCURRENTLY role_assignment_attributes_idx ON role_assignment USING gin(attributes jsonb_path_ops);
