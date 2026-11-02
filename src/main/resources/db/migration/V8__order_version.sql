-- Optimistic locking: concurrent status changes (admin, expiry job, payment webhook) cannot overwrite each other.
ALTER TABLE orders ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
