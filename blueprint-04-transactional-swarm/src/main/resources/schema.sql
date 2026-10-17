CREATE TABLE chat_memory (
  tenant_id       VARCHAR(40)  NOT NULL,
  conversation_id VARCHAR(100) NOT NULL,
  seq             INT          NOT NULL,
  role            VARCHAR(20)  NOT NULL,
  content         CLOB         NOT NULL,
  PRIMARY KEY (tenant_id, conversation_id, seq)
);
CREATE TABLE refunds (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  VARCHAR(40)   NOT NULL,
  order_id   VARCHAR(40)   NOT NULL,
  amount_inr DECIMAL(10,2) NOT NULL,
  reason     VARCHAR(500)
);
CREATE TABLE outbox (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id VARCHAR(40)  NOT NULL,
  payload   VARCHAR(1000) NOT NULL
);

-- Handbook Blueprint 4 (commerce swarm)
CREATE TABLE swarm_chat_memory (
  tenant_id  VARCHAR(64) NOT NULL,
  user_id    VARCHAR(64) NOT NULL,
  session_id VARCHAR(64) NOT NULL,
  seq        INT         NOT NULL,
  role       VARCHAR(20) NOT NULL,
  content    CLOB        NOT NULL,
  PRIMARY KEY (tenant_id, user_id, session_id, seq)
);
CREATE TABLE products (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id   VARCHAR(40)   NOT NULL,
  sku         VARCHAR(40)   NOT NULL,
  name        VARCHAR(200)  NOT NULL,
  description VARCHAR(1000) NOT NULL,
  category    VARCHAR(60)   NOT NULL,
  price_inr   DECIMAL(10,2) NOT NULL,
  stock       INT           NOT NULL,
  version     INT           DEFAULT 0 NOT NULL,
  UNIQUE (tenant_id, sku)
);
CREATE TABLE customer_orders (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id  VARCHAR(40) NOT NULL,
  user_id    VARCHAR(64) NOT NULL,
  status     VARCHAR(20) NOT NULL,
  created_at TIMESTAMP   NOT NULL
);
CREATE TABLE order_items (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id          BIGINT        NOT NULL REFERENCES customer_orders(id),
  line_no           INT           NOT NULL,
  sku               VARCHAR(40)   NOT NULL,
  quantity          INT           NOT NULL,
  unit_price_inr    DECIMAL(10,2) NOT NULL,
  refunded_quantity INT           DEFAULT 0 NOT NULL
);
CREATE TABLE refund_cases (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id     VARCHAR(40)   NOT NULL,
  user_id       VARCHAR(64)   NOT NULL,
  order_id      BIGINT        NOT NULL,
  order_item_id BIGINT        NOT NULL,
  quantity      INT           NOT NULL,
  amount_inr    DECIMAL(10,2) NOT NULL,
  decision      VARCHAR(20)   NOT NULL,
  rationale     VARCHAR(1000),
  policy_rounds INT           NOT NULL,
  created_at    TIMESTAMP     NOT NULL
);
