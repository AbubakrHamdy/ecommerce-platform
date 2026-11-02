-- Orders keep the currency they were placed in, so changing the store's country never rewrites history.
-- Existing rows come from the Egyptian dev store; there is no production data yet.
ALTER TABLE orders ADD COLUMN currency VARCHAR(3);
UPDATE orders SET currency = 'EGP';
ALTER TABLE orders ALTER COLUMN currency SET NOT NULL;
