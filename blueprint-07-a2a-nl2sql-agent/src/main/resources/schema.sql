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

-- Handbook DataAgent: multi-tenant e-commerce schema. Every table carries tenant_id; the SQL guard
-- injects "tenant_id = :tenantId" for each table, so generated SQL never names it.
CREATE TABLE shop_customers (
  tenant_id VARCHAR(20)  NOT NULL,
  id        INT          NOT NULL,
  name      VARCHAR(100) NOT NULL,
  city      VARCHAR(60)  NOT NULL,
  segment   VARCHAR(20)  NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
COMMENT ON TABLE shop_customers IS 'customers buyers clients: name, city, segment (who bought, top customers)';
CREATE TABLE shop_products (
  tenant_id VARCHAR(20)  NOT NULL,
  id        INT          NOT NULL,
  name      VARCHAR(100) NOT NULL,
  category  VARCHAR(40)  NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
COMMENT ON TABLE shop_products IS 'products items catalogue: product name and product category';
CREATE TABLE shop_orders (
  tenant_id   VARCHAR(20) NOT NULL,
  id          INT         NOT NULL,
  customer_id INT         NOT NULL,
  placed_on   DATE        NOT NULL,
  status      VARCHAR(20) NOT NULL,
  PRIMARY KEY (tenant_id, id),
  FOREIGN KEY (tenant_id, customer_id) REFERENCES shop_customers (tenant_id, id)
);
COMMENT ON TABLE shop_orders IS 'orders purchases: when an order was placed (date, month), order status, cancelled orders, order count';
CREATE TABLE shop_order_lines (
  tenant_id      VARCHAR(20)   NOT NULL,
  order_id       INT           NOT NULL,
  line_no        INT           NOT NULL,
  product_id     INT           NOT NULL,
  quantity       INT           NOT NULL,
  unit_price_inr DECIMAL(10,2) NOT NULL,
  PRIMARY KEY (tenant_id, order_id, line_no),
  FOREIGN KEY (tenant_id, order_id) REFERENCES shop_orders (tenant_id, id),
  FOREIGN KEY (tenant_id, product_id) REFERENCES shop_products (tenant_id, id)
);
COMMENT ON TABLE shop_order_lines IS 'order lines: revenue sales amount spend, quantity times unit price in INR, per product';
CREATE TABLE shop_warehouses (
  tenant_id VARCHAR(20) NOT NULL,
  id        INT         NOT NULL,
  site      VARCHAR(60) NOT NULL,
  capacity  INT         NOT NULL,
  PRIMARY KEY (tenant_id, id)
);
COMMENT ON TABLE shop_warehouses IS 'warehouse sites and storage capacity';
