-- Adding a product that is already in the cart increases its quantity; never a second line.
CREATE UNIQUE INDEX uq_cart_items_cart_product ON cart_items (cart_id, product_id);
