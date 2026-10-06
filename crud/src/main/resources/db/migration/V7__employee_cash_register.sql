CREATE TABLE cash_session (
 id SERIAL PRIMARY KEY,
 tenant_id INTEGER NOT NULL REFERENCES client_panel(id),
 account_id INTEGER NOT NULL REFERENCES app_account(id),
 operator_name VARCHAR(255) NOT NULL,
 opened_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 closed_at TIMESTAMPTZ,
 opening_amount NUMERIC(21,2) NOT NULL CHECK(opening_amount>=0),
 counted_amount NUMERIC(21,2) CHECK(counted_amount>=0),
 notes VARCHAR(1000),
 request_id UUID NOT NULL,
 UNIQUE(tenant_id,id), UNIQUE(tenant_id,request_id)
);
CREATE UNIQUE INDEX cash_one_open_per_employee ON cash_session(tenant_id,account_id) WHERE closed_at IS NULL;
CREATE TABLE cash_movement (
 id SERIAL PRIMARY KEY,
 tenant_id INTEGER NOT NULL,
 session_id INTEGER NOT NULL,
 kind VARCHAR(3) NOT NULL CHECK(kind IN ('IN','OUT')),
 amount NUMERIC(21,2) NOT NULL CHECK(amount>0),
 reason VARCHAR(500) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 request_id UUID NOT NULL,
 UNIQUE(tenant_id,request_id),
 FOREIGN KEY(tenant_id,session_id) REFERENCES cash_session(tenant_id,id)
);
ALTER TABLE store_sale ADD COLUMN cash_session_id INTEGER;
ALTER TABLE sale_payment ADD COLUMN cash_session_id INTEGER;
ALTER TABLE sale_payment ADD COLUMN payment_method VARCHAR(10) CHECK(payment_method IN ('CASH','PIX','DEBIT','CARD'));
ALTER TABLE stock_replenishment ADD COLUMN cash_session_id INTEGER;
ALTER TABLE store_sale ADD FOREIGN KEY(tenant_id,cash_session_id) REFERENCES cash_session(tenant_id,id);
ALTER TABLE sale_payment ADD FOREIGN KEY(tenant_id,cash_session_id) REFERENCES cash_session(tenant_id,id);
ALTER TABLE stock_replenishment ADD FOREIGN KEY(tenant_id,cash_session_id) REFERENCES cash_session(tenant_id,id);
CREATE INDEX cash_sales_idx ON store_sale(tenant_id,cash_session_id);
CREATE INDEX cash_payments_idx ON sale_payment(tenant_id,cash_session_id);
