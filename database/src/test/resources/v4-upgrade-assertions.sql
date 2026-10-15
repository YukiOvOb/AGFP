DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM serms.approval_decision WHERE approval_id='00000000-0000-0000-0000-000000004005'
   AND decision='REJECTED' AND comment='Keep this reason') THEN RAISE EXCEPTION 'V004 lost decision history'; END IF;
 IF (SELECT count(*) FROM information_schema.columns WHERE table_schema='serms'
   AND table_name IN ('equipment','reservation','loan','maintenance_case') AND column_name='version'
   AND data_type='bigint') <> 4 THEN RAISE EXCEPTION 'V004 version alignment failed'; END IF;
 BEGIN
   UPDATE serms.approval_decision SET comment='Illegal rewrite' WHERE approval_id='00000000-0000-0000-0000-000000004005';
   RAISE EXCEPTION 'Immutable trigger was not restored';
 EXCEPTION WHEN check_violation THEN NULL;
 END;
END $$;
