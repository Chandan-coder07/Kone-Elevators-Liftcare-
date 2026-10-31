INSERT INTO buildings(name,address) VALUES ('Green Park Residency','12 Temple Road, Tiruchengode');
INSERT INTO elevators(building_id,label,model) VALUES (1,'E-01','KONE MonoSpace'),(1,'E-02','KONE MonoSpace'),(1,'E-03','Otis Gen2');
INSERT INTO users(name,email,phone,role,building_id) VALUES
 ('Admin','admin@liftcare.test',NULL,'ADMIN',1),
 ('Asha (Resident)','asha@liftcare.test','+910000000001','RESIDENT',1),
 ('Ravi (Technician)','ravi@liftcare.test','+910000000003','TECHNICIAN',1),
 ('Mani (Technician)','mani@liftcare.test','+910000000004','TECHNICIAN',1),
 ('Priya (Service Supervisor)','priya@liftcare.test','+910000000005','COMPANY_SUPERVISOR',NULL),
 ('Kumar (Building Manager)','kumar@liftcare.test','+910000000006','BUILDING_MANAGER',1);

INSERT INTO incidents(incident_id,elevator_id,reported_by,category,description,is_emergency,priority,status,reported_at,acknowledgement_due,arrival_due,resolution_due,red_flag,escalated_at,escalation_reason,downtime_start) VALUES
 ('INC-2026-0001',2,2,'DOOR_MALFUNCTION','Door keeps reopening on 3rd floor',false,'P2','ASSIGNED',now()-interval '40 minutes',now()+interval '20 minutes',now()+interval '80 minutes',now()+interval '140 minutes',false,NULL,NULL,now()-interval '40 minutes'),
 ('INC-2026-0002',1,2,'PASSENGER_TRAPPED','Passenger stuck between floors',true,'P0','ASSIGNED',now()-interval '6 minutes',now()+interval '4 minutes',now()+interval '24 minutes',now()+interval '174 minutes',true,now()-interval '5 minutes','Critical safety incident - immediate escalation',now()-interval '6 minutes'),
 ('INC-2026-0003',3,2,'NOISE','Grinding noise during descent',false,'P3','IN_PROGRESS',now()-interval '5 hours',now()-interval '4 hours',now()-interval '3 hours',now()-interval '30 minutes',true,now()-interval '30 minutes','Resolution deadline missed',NULL);
INSERT INTO assignments(incident_id,technician_id,assigned_by,status,assigned_at,acknowledged_at,arrived_at) VALUES
 ('INC-2026-0001',3,5,'ASSIGNED',now()-interval '35 minutes',NULL,NULL),
 ('INC-2026-0002',3,5,'ASSIGNED',now()-interval '5 minutes',NULL,NULL),
 ('INC-2026-0003',4,5,'IN_PROGRESS',now()-interval '4 hours',now()-interval '3 hours 50 minutes',now()-interval '3 hours');
UPDATE incidents SET acknowledged_at=now()-interval '3 hours 50 minutes', arrived_at=now()-interval '3 hours' WHERE incident_id='INC-2026-0003';
INSERT INTO notification_outbox(incident_id,kind,channel,recipient,subject,body,status,sent_at) VALUES
 ('INC-2026-0002','ESCALATION','SMS','+910000000005','CRITICAL INC-2026-0002','Passenger trapped in E-01','SENT',now()-interval '4 minutes'),
 ('INC-2026-0003','ESCALATION','EMAIL','priya@liftcare.test','Overdue INC-2026-0003','Resolution deadline missed','DELIVERED',now()-interval '29 minutes');

-- 56 days of resolved history for analytics (E-02 has repeated door faults)
INSERT INTO incidents(incident_id,elevator_id,category,priority,status,reported_at,acknowledged_at,arrived_at,resolved_at,verified_at,resolution_due,red_flag,downtime_start)
SELECT 'INC-H-'||g, CASE WHEN g%3=2 THEN 1+(g%2)*2 ELSE 2 END,
       CASE WHEN g%3<2 THEN 'DOOR_MALFUNCTION' ELSE 'NOISE' END, (ARRAY['P1','P2','P2','P3'])[1+g%4], 'VERIFIED_RESOLVED',
       t, t+interval '12 minutes', t+interval '55 minutes', t+(60+(g%5)*45)*interval '1 minute', t+(90+(g%5)*45)*interval '1 minute',
       t+interval '3 hours', (g%7=0), t
FROM (SELECT g, now()-(g*interval '2 days')-interval '1 hour' AS t FROM generate_series(1,28) g) s;
