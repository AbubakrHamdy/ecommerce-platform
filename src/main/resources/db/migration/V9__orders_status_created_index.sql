-- Supports the expiry job (PENDING orders older than the payment window) and admin filtering by status.
CREATE INDEX idx_orders_status_created_at ON orders (status, created_at);
