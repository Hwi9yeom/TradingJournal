-- Security Enhancement: Add user ownership to accounts
-- This migration adds user_id column to accounts table for IDOR prevention

-- Add user_id column to accounts table
ALTER TABLE accounts ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- Create index for user-based queries
CREATE INDEX IF NOT EXISTS idx_account_user_id ON accounts(user_id);

-- Create composite index for user + default account queries
CREATE INDEX IF NOT EXISTS idx_account_user_default ON accounts(user_id, is_default);

-- Note: Existing accounts will have NULL user_id.
-- These should be assigned to users during data migration or on first access.
-- The application handles NULL user_id gracefully for backward compatibility.
