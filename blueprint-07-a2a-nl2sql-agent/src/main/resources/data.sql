-- synthetic data
INSERT INTO customers VALUES (1,'Asha Rao','Chennai'),(2,'Vikram Iyer','Bengaluru'),(3,'Meera Nair','Kochi'),(4,'Arjun Das','Chennai');
INSERT INTO orders VALUES
 (1,1,1499.00,DATE '2026-09-01'),(2,1,250.50,DATE '2026-09-15'),(3,2,9999.00,DATE '2026-09-20'),
 (4,3,120.00,DATE '2026-10-01'),(5,4,560.75,DATE '2026-10-05'),(6,4,3300.00,DATE '2026-10-10');
-- 600 extra orders for customer 2, to prove LIMIT 500
INSERT INTO orders SELECT 100 + X, 2, 10.00, DATE '2026-08-01' FROM SYSTEM_RANGE(1, 600);

-- Handbook DataAgent seed (synthetic). Two tenants with different numbers.
INSERT INTO shop_customers VALUES
 ('acme',1,'Asha Rao','Chennai','RETAIL'),('acme',2,'Vikram Iyer','Bengaluru','B2B'),('acme',3,'Meera Nair','Chennai','RETAIL'),
 ('acme',4,'Arjun Das','Pune','B2B'),('globex',1,'Ben Okafor','Chennai','RETAIL'),('globex',2,'Lena Fischer','Pune','B2B');
INSERT INTO shop_products VALUES
 ('acme',10,'Noise-cancelling headphones','AUDIO'),('acme',11,'Wireless earbuds','AUDIO'),('acme',12,'Mechanical keyboard','PERIPHERALS'),
 ('acme',13,'Ergonomic mouse','PERIPHERALS'),('acme',14,'USB-C dock','ACCESSORIES'),
 ('globex',10,'Studio monitor','AUDIO'),('globex',11,'Desk lamp','ACCESSORIES');
INSERT INTO shop_orders VALUES
 ('acme',100,1,DATE '2026-08-03','DELIVERED'),('acme',101,1,DATE '2026-09-12','DELIVERED'),('acme',102,2,DATE '2026-09-20','DELIVERED'),
 ('acme',103,3,DATE '2026-10-01','CANCELLED'),('acme',104,4,DATE '2026-10-05','PLACED'),('acme',105,3,DATE '2026-10-09','DELIVERED'),
 ('globex',100,1,DATE '2026-09-02','DELIVERED'),('globex',101,2,DATE '2026-10-02','CANCELLED');
INSERT INTO shop_order_lines VALUES
 ('acme',100,1,10,1,8999.00),('acme',100,2,13,1,1299.00),('acme',101,1,11,2,2499.00),('acme',102,1,12,5,4599.00),
 ('acme',102,2,14,5,3499.00),('acme',103,1,10,1,8999.00),('acme',104,1,13,3,1299.00),('acme',105,1,14,1,3499.00),
 ('globex',100,1,10,2,15999.00),('globex',101,1,11,4,1999.00);
INSERT INTO shop_warehouses VALUES ('acme',1,'Hosur',12000),('globex',1,'Chakan',8000);
