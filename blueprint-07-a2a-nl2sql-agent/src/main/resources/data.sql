-- synthetic data
INSERT INTO customers VALUES (1,'Asha Rao','Chennai'),(2,'Vikram Iyer','Bengaluru'),(3,'Meera Nair','Kochi'),(4,'Arjun Das','Chennai');
INSERT INTO orders VALUES
 (1,1,1499.00,DATE '2026-09-01'),(2,1,250.50,DATE '2026-09-15'),(3,2,9999.00,DATE '2026-09-20'),
 (4,3,120.00,DATE '2026-10-01'),(5,4,560.75,DATE '2026-10-05'),(6,4,3300.00,DATE '2026-10-10');
-- 600 extra orders for customer 2, to prove LIMIT 500
INSERT INTO orders SELECT 100 + X, 2, 10.00, DATE '2026-08-01' FROM SYSTEM_RANGE(1, 600);
