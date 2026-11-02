ALTER TABLE product_images ADD COLUMN storage_key VARCHAR(255);

-- Sibling categories must have distinct names (case-insensitive); top-level categories share parent 0.
CREATE UNIQUE INDEX uq_categories_parent_name ON categories (COALESCE(parent_id, 0), LOWER(name));
