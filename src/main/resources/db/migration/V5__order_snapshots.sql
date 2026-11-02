-- An order must not change when the customer later edits or deletes an address,
-- so the shipping address is copied into the order instead of referenced.
ALTER TABLE orders
    ADD COLUMN shipping_street      VARCHAR(150),
    ADD COLUMN shipping_city        VARCHAR(100),
    ADD COLUMN shipping_state       VARCHAR(100),
    ADD COLUMN shipping_postal_code VARCHAR(20),
    ADD COLUMN shipping_country     VARCHAR(100);

UPDATE orders o
SET shipping_street      = a.street,
    shipping_city        = a.city,
    shipping_state       = a.state,
    shipping_postal_code = a.postal_code,
    shipping_country     = a.country
FROM addresses a
WHERE a.id = o.address_id;

ALTER TABLE orders
    ALTER COLUMN shipping_street SET NOT NULL,
    ALTER COLUMN shipping_city SET NOT NULL,
    ALTER COLUMN shipping_postal_code SET NOT NULL,
    ALTER COLUMN shipping_country SET NOT NULL,
    DROP COLUMN address_id;

-- Same reasoning for the product name shown in order history.
ALTER TABLE order_items ADD COLUMN product_name VARCHAR(150);

UPDATE order_items oi
SET product_name = p.name
FROM products p
WHERE p.id = oi.product_id;

ALTER TABLE order_items ALTER COLUMN product_name SET NOT NULL;
