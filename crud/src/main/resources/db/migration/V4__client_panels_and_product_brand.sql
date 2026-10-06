CREATE TABLE client_panel (
 id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, slug VARCHAR(40) NOT NULL,
 active BOOLEAN NOT NULL DEFAULT TRUE, auth_version INTEGER NOT NULL DEFAULT 0,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE UNIQUE INDEX client_panel_slug_unique ON client_panel (LOWER(slug));
INSERT INTO client_panel (name,slug) VALUES ('Painel principal','principal');
ALTER TABLE app_account ADD COLUMN tenant_id INTEGER REFERENCES client_panel(id);
ALTER TABLE category ADD COLUMN tenant_id INTEGER REFERENCES client_panel(id);
ALTER TABLE product ADD COLUMN tenant_id INTEGER REFERENCES client_panel(id);
UPDATE app_account SET tenant_id=1;
UPDATE category SET tenant_id=1;
UPDATE product SET tenant_id=1;
ALTER TABLE app_account ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE category ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE product ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE app_account DROP CONSTRAINT app_account_role_check;
ALTER TABLE app_account ADD CONSTRAINT app_account_role_check CHECK(role IN ('OWNER','ADMIN','USER'));
UPDATE app_account SET role='OWNER',auth_version=auth_version+1
 WHERE id=(SELECT MIN(id) FROM app_account WHERE role='ADMIN');
DROP INDEX app_account_email_unique;
CREATE UNIQUE INDEX app_account_tenant_email_unique ON app_account(tenant_id,LOWER(email));
DROP INDEX category_name_unique;
CREATE UNIQUE INDEX category_tenant_name_unique ON category(tenant_id,LOWER(name));
ALTER TABLE category ADD CONSTRAINT category_tenant_id_unique UNIQUE(tenant_id,id);
ALTER TABLE product DROP CONSTRAINT product_category_id_fkey;
ALTER TABLE product ADD CONSTRAINT product_tenant_category_fkey FOREIGN KEY(tenant_id,category_id) REFERENCES category(tenant_id,id);
ALTER TABLE product ADD COLUMN brand_name VARCHAR(150);
UPDATE product p SET brand_name=b.name FROM brand b WHERE p.brand_id=b.id;
ALTER TABLE product DROP COLUMN brand_id;
DROP INDEX product_barcode_unique;
DROP INDEX product_sku_unique;
CREATE UNIQUE INDEX product_tenant_barcode_unique ON product(tenant_id,barcode) WHERE barcode IS NOT NULL;
CREATE UNIQUE INDEX product_tenant_sku_unique ON product(tenant_id,LOWER(sku)) WHERE sku IS NOT NULL;
CREATE INDEX product_tenant_idx ON product(tenant_id);
CREATE INDEX account_tenant_idx ON app_account(tenant_id);
