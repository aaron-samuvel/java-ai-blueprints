CREATE TABLE customers (
  id      INT PRIMARY KEY,
  name    VARCHAR(100) NOT NULL,
  city    VARCHAR(60)  NOT NULL
);
CREATE TABLE orders (
  id          INT PRIMARY KEY,
  customer_id INT NOT NULL REFERENCES customers(id),
  amount_inr  DECIMAL(10,2) NOT NULL,
  placed_on   DATE NOT NULL
);
