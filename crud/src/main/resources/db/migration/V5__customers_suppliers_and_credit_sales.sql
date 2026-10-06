CREATE TABLE store_customer (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES client_panel(id),
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(255),
    address VARCHAR(500),
    notes VARCHAR(3000),
    UNIQUE (tenant_id, id)
);

CREATE TABLE supplier (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES client_panel(id),
    name VARCHAR(150) NOT NULL,
    phone VARCHAR(40),
    email VARCHAR(255),
    address VARCHAR(500),
    supplied_items VARCHAR(5000),
    notes VARCHAR(3000)
);

CREATE TABLE store_sale (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL REFERENCES client_panel(id),
    customer_id INTEGER,
    payment_type VARCHAR(10) NOT NULL CHECK (payment_type IN ('CASH', 'CREDIT')),
    total NUMERIC(21,2) NOT NULL CHECK (total >= 0),
    sale_date DATE NOT NULL,
    due_date DATE,
    notes VARCHAR(3000),
    request_id UUID NOT NULL,
    created_by INTEGER NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, id),
    UNIQUE (tenant_id, request_id),
    CHECK (payment_type <> 'CREDIT' OR customer_id IS NOT NULL),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES store_customer(tenant_id, id)
);

ALTER TABLE product ADD CONSTRAINT product_tenant_id_unique UNIQUE (tenant_id, id);
CREATE TABLE store_sale_item (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    sale_id INTEGER NOT NULL,
    product_id INTEGER NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(21,2) NOT NULL CHECK (unit_price >= 0),
    FOREIGN KEY (tenant_id, sale_id) REFERENCES store_sale(tenant_id, id),
    FOREIGN KEY (tenant_id, product_id) REFERENCES product(tenant_id, id)
);

CREATE TABLE sale_payment (
    id SERIAL PRIMARY KEY,
    tenant_id INTEGER NOT NULL,
    sale_id INTEGER NOT NULL,
    amount NUMERIC(21,2) NOT NULL CHECK (amount > 0),
    payment_date DATE NOT NULL,
    notes VARCHAR(1000),
    request_id UUID NOT NULL,
    created_by INTEGER NOT NULL REFERENCES app_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (tenant_id, request_id),
    FOREIGN KEY (tenant_id, sale_id) REFERENCES store_sale(tenant_id, id)
);

CREATE INDEX store_customer_tenant_idx ON store_customer(tenant_id);
CREATE INDEX supplier_tenant_idx ON supplier(tenant_id);
CREATE INDEX store_sale_customer_idx ON store_sale(tenant_id, customer_id);
CREATE INDEX sale_payment_sale_idx ON sale_payment(tenant_id, sale_id);
CREATE INDEX store_sale_item_sale_idx ON store_sale_item(tenant_id, sale_id);
