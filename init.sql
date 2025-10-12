-- init.sql - Database initialization script for remake_bot
-- This script creates all tables used by the bot

-- Table for storing message statistics
-- Tracks message counts per user per hour
CREATE TABLE IF NOT EXISTS message_stats (
    telegram_id BIGINT NOT NULL,
    hour_ts TIMESTAMP NOT NULL,
    count INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (telegram_id, hour_ts)
);

-- Create index for efficient lookups by time range
CREATE INDEX IF NOT EXISTS idx_message_stats_hour_ts ON message_stats(hour_ts);

-- Table for storing quotes/messages from users
-- Used for inline query functionality
CREATE TABLE IF NOT EXISTS result_new (
    id SERIAL PRIMARY KEY,
    text TEXT NOT NULL,
    "from" TEXT NOT NULL,
    from_id TEXT NOT NULL
);

-- Create index for efficient text searches
CREATE INDEX IF NOT EXISTS idx_result_new_text ON result_new(text);
CREATE INDEX IF NOT EXISTS idx_result_new_from_id ON result_new(from_id);
