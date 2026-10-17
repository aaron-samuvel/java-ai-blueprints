-- Demo catalogue and order history for two tenants (Blueprint 4 commerce swarm).
INSERT INTO products (tenant_id, sku, name, description, category, price_inr, stock) VALUES
 ('acme', 'SKU-HDP-01', 'Studio wireless headphones', 'Over-ear wireless headphones with active noise cancelling and 30 hour battery', 'audio', 7999.00, 25),
 ('acme', 'SKU-EAR-02', 'Running earbuds', 'Sweat-proof wireless earbuds for running and the gym, secure fit', 'audio', 2499.00, 40),
 ('acme', 'SKU-KBD-03', 'Mechanical keyboard', 'Compact mechanical keyboard with hot-swappable switches and backlight', 'computing', 5499.00, 3),
 ('acme', 'SKU-MSE-04', 'Ergonomic mouse', 'Vertical ergonomic wireless mouse that reduces wrist strain', 'computing', 1999.00, 15),
 ('globex', 'SKU-HDP-01', 'Globex noise cancelling headphones', 'Wireless over-ear headphones with noise cancelling, sold by Globex', 'audio', 8999.00, 10);
INSERT INTO customer_orders (id, tenant_id, user_id, status, created_at) VALUES
 (1001, 'acme', 'u-asha', 'DELIVERED', DATEADD('DAY', -5, CURRENT_TIMESTAMP)),
 (1002, 'acme', 'u-asha', 'DELIVERED', DATEADD('DAY', -60, CURRENT_TIMESTAMP)),
 (2001, 'globex', 'u-ben', 'DELIVERED', DATEADD('DAY', -3, CURRENT_TIMESTAMP));
INSERT INTO order_items (id, order_id, line_no, sku, quantity, unit_price_inr, refunded_quantity) VALUES
 (5001, 1001, 0, 'SKU-HDP-01', 1, 7999.00, 0),
 (5002, 1001, 1, 'SKU-EAR-02', 2, 2499.00, 0),
 (5003, 1002, 0, 'SKU-MSE-04', 1, 1999.00, 0),
 (6001, 2001, 0, 'SKU-HDP-01', 1, 8999.00, 0);
ALTER TABLE customer_orders ALTER COLUMN id RESTART WITH 3000;
ALTER TABLE order_items ALTER COLUMN id RESTART WITH 7000;
