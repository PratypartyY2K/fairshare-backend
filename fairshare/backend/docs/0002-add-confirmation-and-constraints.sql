-- Migration: add confirmation_id to confirmed_transfers, idempotency_key and voided to expenses
-- Run this against your Postgres database

-- 1) Add confirmation_id to confirmed_transfers
ALTER TABLE confirmed_transfers
  ADD COLUMN IF NOT EXISTS confirmation_id varchar(128);

-- Create unique index to dedupe confirmations per group (allows NULLs)
CREATE UNIQUE INDEX IF NOT EXISTS uq_confirmed_transfers_group_confirmation ON confirmed_transfers(group_id, confirmation_id);

-- 2) Add idempotency_key and voided to expenses
ALTER TABLE expenses
  ADD COLUMN IF NOT EXISTS idempotency_key varchar(128);

CREATE UNIQUE INDEX IF NOT EXISTS uq_expenses_group_idempotency ON expenses(group_id, idempotency_key);

ALTER TABLE expenses
  ADD COLUMN IF NOT EXISTS voided boolean DEFAULT false;

-- Make voided NOT NULL with default false
ALTER TABLE expenses ALTER COLUMN voided SET DEFAULT false;
ALTER TABLE expenses ALTER COLUMN voided SET NOT NULL;
