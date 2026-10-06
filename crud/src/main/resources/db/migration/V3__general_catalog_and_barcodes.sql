ALTER TABLE product ALTER COLUMN price TYPE NUMERIC(21,2) USING price::NUMERIC(21,2);
ALTER TABLE product ADD COLUMN barcode VARCHAR(100);
ALTER TABLE product ADD COLUMN sku VARCHAR(100);
ALTER TABLE product ADD COLUMN model VARCHAR(150);
ALTER TABLE product ADD COLUMN description VARCHAR(2000);
ALTER TABLE product ADD COLUMN quantity INTEGER NOT NULL DEFAULT 0 CHECK (quantity >= 0);
CREATE UNIQUE INDEX product_barcode_unique ON product (barcode) WHERE barcode IS NOT NULL;
CREATE UNIQUE INDEX product_sku_unique ON product (LOWER(sku)) WHERE sku IS NOT NULL;
INSERT INTO category (name) VALUES
 ('Computadores'),('Celulares'),('Eletrônicos'),('Eletrodomésticos'),('Acessórios'),
 ('Roupas e calçados'),('Ferramentas'),('Móveis'),('Papelaria'),('Outros')
ON CONFLICT DO NOTHING;
