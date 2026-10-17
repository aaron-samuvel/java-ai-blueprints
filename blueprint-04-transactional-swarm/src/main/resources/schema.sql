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
