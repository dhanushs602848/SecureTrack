-- Add google_sub_id column for Google Sign-In authentication

ALTER TABLE users ADD COLUMN IF NOT EXISTS google_sub_id VARCHAR(255) UNIQUE AFTER phone_number;
