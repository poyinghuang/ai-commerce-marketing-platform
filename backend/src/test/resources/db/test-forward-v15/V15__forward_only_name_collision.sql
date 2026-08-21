-- Test-only forward collision. Not shipped as a real V15 on the main-bound branch.
CREATE FUNCTION is_stage4c_owned_operation(p_operation_uuid UUID) RETURNS BOOLEAN LANGUAGE sql AS $$ SELECT false $$;
