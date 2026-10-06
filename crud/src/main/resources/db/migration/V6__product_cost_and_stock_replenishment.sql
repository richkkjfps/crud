ALTER TABLE product ADD COLUMN cost_price NUMERIC(21,2) CHECK (cost_price >= 0);
ALTER TABLE store_sale_item ADD COLUMN unit_cost NUMERIC(21,2) CHECK (unit_cost >= 0);

CREATE TABLE stock_replenishment (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    stock_before INTEGER NOT NULL,
    stock_after INTEGER NOT NULL,
    cost_price NUMERIC(21,2),
    sale_price NUMERIC(21,2) NOT NULL,
    request_id UUID NOT NULL,
    created_by INTEGER NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, request_id),
    FOREIGN KEY (tenant_id,product_id) REFERENCES product(tenant_id,id)
);
