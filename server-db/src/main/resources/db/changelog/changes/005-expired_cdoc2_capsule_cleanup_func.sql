-- liquibase formatted sql
-- changeset expired_cdoc2_capsule_cleanup_func:5 runOnChange:true
CREATE OR REPLACE FUNCTION expired_cdoc2_capsule_cleanup()
    RETURNS INTEGER
AS '
    DECLARE deleted INTEGER := 0;
BEGIN
	DELETE FROM cdoc2_capsule WHERE transaction_id IN
		(SELECT transaction_id FROM cdoc2_capsule WHERE expiry_time < CURRENT_TIMESTAMP LIMIT 1000);
	GET DIAGNOSTICS deleted = ROW_COUNT;
	RETURN deleted;
END
'
LANGUAGE plpgsql;
