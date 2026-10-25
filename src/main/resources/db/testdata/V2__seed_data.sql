INSERT INTO users (email, password, first_name, last_name, role, auth_provider, created_at, updated_at) VALUES
('admin@example.com', '$2a$10$QUJ/Vnzt9n5CfCV8mkvl3OKIBtuOUQW5lQFAJKRiaht3H2T9UFiFi', 'Admin', 'User', 'ADMIN', 'LOCAL', NOW(), NOW()),
('customer@example.com', '$2a$10$jy8Ycv.2UuNe.G5NHAgAGeSG2vhuvmOqdJe4hWTCajhkoPFVEMQpq', 'Jane', 'Customer', 'CUSTOMER', 'LOCAL', NOW(), NOW());

INSERT INTO categories (name, parent_id, created_at, updated_at) VALUES
('Electronics', NULL, NOW(), NOW()),
('Clothing', NULL, NOW(), NOW());

INSERT INTO categories (name, parent_id, created_at, updated_at) VALUES
('Laptops', (SELECT id FROM categories WHERE name = 'Electronics'), NOW(), NOW()),
('Men''s Clothing', (SELECT id FROM categories WHERE name = 'Clothing'), NOW(), NOW());

INSERT INTO products (name, description, price, stock, active, category_id, created_at, updated_at) VALUES
('ProBook 14 Laptop', 'A reliable 14-inch laptop for work and study, 16GB RAM, 512GB SSD.', 899.99, 25, true, (SELECT id FROM categories WHERE name = 'Laptops'), NOW(), NOW()),
('UltraSlim 13 Laptop', 'Lightweight ultrabook with all-day battery life.', 1199.00, 15, true, (SELECT id FROM categories WHERE name = 'Laptops'), NOW(), NOW()),
('Wireless Mouse', 'Ergonomic wireless mouse with USB receiver.', 19.99, 150, true, (SELECT id FROM categories WHERE name = 'Electronics'), NOW(), NOW()),
('Classic Cotton T-Shirt', '100% cotton crew neck t-shirt.', 14.99, 200, true, (SELECT id FROM categories WHERE name = 'Men''s Clothing'), NOW(), NOW()),
('Denim Jacket', 'Classic fit denim jacket.', 59.99, 40, true, (SELECT id FROM categories WHERE name = 'Men''s Clothing'), NOW(), NOW()),
('Discontinued Headphones', 'Over-ear headphones (discontinued model).', 29.99, 0, false, (SELECT id FROM categories WHERE name = 'Electronics'), NOW(), NOW());

INSERT INTO product_images (url, is_primary, display_order, product_id, created_at, updated_at) VALUES
('https://picsum.photos/seed/probook14/600/600', true, 0, (SELECT id FROM products WHERE name = 'ProBook 14 Laptop'), NOW(), NOW()),
('https://picsum.photos/seed/probook14b/600/600', false, 1, (SELECT id FROM products WHERE name = 'ProBook 14 Laptop'), NOW(), NOW()),
('https://picsum.photos/seed/ultraslim13/600/600', true, 0, (SELECT id FROM products WHERE name = 'UltraSlim 13 Laptop'), NOW(), NOW()),
('https://picsum.photos/seed/mouse/600/600', true, 0, (SELECT id FROM products WHERE name = 'Wireless Mouse'), NOW(), NOW()),
('https://picsum.photos/seed/tshirt/600/600', true, 0, (SELECT id FROM products WHERE name = 'Classic Cotton T-Shirt'), NOW(), NOW()),
('https://picsum.photos/seed/jacket/600/600', true, 0, (SELECT id FROM products WHERE name = 'Denim Jacket'), NOW(), NOW());

INSERT INTO addresses (street, city, state, postal_code, country, is_default, user_id, created_at, updated_at) VALUES
('123 Tahrir Street', 'Cairo', 'Cairo Governorate', '11511', 'Egypt', true, (SELECT id FROM users WHERE email = 'customer@example.com'), NOW(), NOW());

INSERT INTO carts (user_id, created_at, updated_at) VALUES
((SELECT id FROM users WHERE email = 'customer@example.com'), NOW(), NOW());

INSERT INTO cart_items (quantity, cart_id, product_id, created_at, updated_at) VALUES
(1, (SELECT id FROM carts WHERE user_id = (SELECT id FROM users WHERE email = 'customer@example.com')), (SELECT id FROM products WHERE name = 'Wireless Mouse'), NOW(), NOW()),
(2, (SELECT id FROM carts WHERE user_id = (SELECT id FROM users WHERE email = 'customer@example.com')), (SELECT id FROM products WHERE name = 'Classic Cotton T-Shirt'), NOW(), NOW());

INSERT INTO orders (status, total_amount, user_id, address_id, created_at, updated_at) VALUES
('PAID', 899.99, (SELECT id FROM users WHERE email = 'customer@example.com'), (SELECT id FROM addresses WHERE user_id = (SELECT id FROM users WHERE email = 'customer@example.com')), NOW(), NOW());

INSERT INTO order_items (price_at_purchase, quantity, order_id, product_id, created_at, updated_at) VALUES
(899.99, 1, (SELECT id FROM orders WHERE user_id = (SELECT id FROM users WHERE email = 'customer@example.com') LIMIT 1), (SELECT id FROM products WHERE name = 'ProBook 14 Laptop'), NOW(), NOW());

INSERT INTO payments (provider, status, transaction_id, amount, order_id, created_at, updated_at) VALUES
('STRIPE', 'SUCCEEDED', 'pi_test_1234567890', 899.99, (SELECT id FROM orders WHERE user_id = (SELECT id FROM users WHERE email = 'customer@example.com') LIMIT 1), NOW(), NOW());

INSERT INTO reviews (rating, comment, user_id, product_id, created_at, updated_at) VALUES
(5, 'Great laptop, fast shipping and exactly as described!', (SELECT id FROM users WHERE email = 'customer@example.com'), (SELECT id FROM products WHERE name = 'ProBook 14 Laptop'), NOW(), NOW());
