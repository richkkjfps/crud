CREATE TABLE app_account (
 id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(255) NOT NULL,
 password_hash VARCHAR(255) NOT NULL,
 role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('ADMIN','USER')),
 active BOOLEAN NOT NULL DEFAULT TRUE, auth_version INTEGER NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX app_account_email_unique ON app_account (LOWER(email));
CREATE TABLE category (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL);
CREATE UNIQUE INDEX category_name_unique ON category (LOWER(name));
CREATE TABLE brand (id SERIAL PRIMARY KEY, name VARCHAR(100) NOT NULL);
CREATE UNIQUE INDEX brand_name_unique ON brand (LOWER(name));
INSERT INTO category (name) VALUES ('Comida'),('Alimentos'),('Limpeza');
ALTER TABLE product ADD COLUMN category_id INTEGER REFERENCES category(id);
ALTER TABLE product ADD COLUMN brand_id INTEGER REFERENCES brand(id);
CREATE INDEX product_category_idx ON product(category_id);
CREATE INDEX product_brand_idx ON product(brand_id);

