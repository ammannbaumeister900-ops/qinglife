CREATE TABLE ql_habit_pause (
  habit_plan_id char(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  start_date date NOT NULL,
  end_date date DEFAULT NULL,
  PRIMARY KEY (habit_plan_id, start_date),
  CONSTRAINT fk_ql_habit_pause_plan FOREIGN KEY (habit_plan_id) REFERENCES ql_habit_plan(id),
  CONSTRAINT ck_ql_habit_pause_dates CHECK (end_date IS NULL OR end_date >= start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
-- Preserve any legacy open pause; old closed intervals were not recorded.
INSERT INTO ql_habit_pause(habit_plan_id,start_date,end_date)
SELECT id, GREATEST(started_at,DATE(COALESCE(paused_at,updated_at))), NULL
FROM ql_habit_plan WHERE status='paused';