-- Migration: add idempotency_key to expenses table
-- Run this against your production database (Postgres)

ALTER TABLE expenses
  ADD COLUMN idempotency_key varchar(128);

-- Optional index for faster lookup by group and idempotency key
CREATE INDEX IF NOT EXISTS idx_expenses_group_idempotency ON expenses(group_id, idempotency_key);
