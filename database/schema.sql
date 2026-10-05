-- SmartCare Pharmacy Web Portal - MySQL 8 schema
-- Optional: the application creates the same tables automatically (spring.jpa.hibernate.ddl-auto=update).
-- Run this script if you prefer to create the database manually, e.g.:  mysql -u root -p < database/schema.sql
-- Demo users/medicines are inserted by the application on first start (DataInitializer).

CREATE DATABASE IF NOT EXISTS smartcare CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smartcare;

CREATE TABLE IF NOT EXISTS users (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  username    VARCHAR(50)  NOT NULL UNIQUE,
  password    VARCHAR(255) NOT NULL,                       -- BCrypt hash
  full_name   VARCHAR(100) NOT NULL,
  email       VARCHAR(100),
  phone       VARCHAR(20),
  address     VARCHAR(255),
  role        VARCHAR(20)  NOT NULL,                       -- ADMIN, PHARMACIST, CUSTOMER, SUPPLIER, STORE_MANAGER, DELIVERY_STAFF
  status      VARCHAR(20)  NOT NULL,                       -- PENDING, ACTIVE, REJECTED, DEACTIVATED
  created_at  DATETIME(6),
  CONSTRAINT chk_users_role CHECK (role IN ('ADMIN','PHARMACIST','CUSTOMER','SUPPLIER','STORE_MANAGER','DELIVERY_STAFF')),
  CONSTRAINT chk_users_status CHECK (status IN ('PENDING','ACTIVE','REJECTED','DEACTIVATED'))
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS medicine (
  id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
  version               BIGINT,                             -- optimistic locking (prevents overselling)
  name                  VARCHAR(100)  NOT NULL,
  generic_name          VARCHAR(100),
  category              VARCHAR(255)  NOT NULL,
  manufacturer          VARCHAR(255),
  dosage_form           VARCHAR(255),
  price                 DECIMAL(10,2) NOT NULL,
  stock                 INT NOT NULL,
  reorder_level         INT NOT NULL,
  batch_no              VARCHAR(255),
  expiry_date           DATE NOT NULL,
  prescription_required BIT(1) NOT NULL,
  active                BIT(1) NOT NULL,
  image_file            VARCHAR(255),                       -- optional product photo (file name in ./uploads)
  CONSTRAINT chk_medicine_stock CHECK (stock >= 0),          -- data integrity: stock can never go negative
  CONSTRAINT chk_medicine_price CHECK (price > 0)
) ENGINE=InnoDB;
CREATE INDEX idx_medicine_name ON medicine(name);

CREATE TABLE IF NOT EXISTS prescription (
  id               BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id      BIGINT NOT NULL,
  doctor_name      VARCHAR(255),
  file_name        VARCHAR(255),
  original_name    VARCHAR(255),
  status           VARCHAR(20) NOT NULL,                     -- PENDING, APPROVED, REJECTED
  pharmacist_note  VARCHAR(255),
  uploaded_at      DATETIME(6),
  verified_by_id   BIGINT,
  FOREIGN KEY (customer_id)    REFERENCES users(id),
  FOREIGN KEY (verified_by_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS orders (
  id                BIGINT AUTO_INCREMENT PRIMARY KEY,
  customer_id       BIGINT NOT NULL,
  status            VARCHAR(20) NOT NULL,                    -- PLACED, VERIFIED, PACKED, DISPATCHED, DELIVERED, REJECTED
  payment_method    VARCHAR(20) NOT NULL,                    -- CARD, MOBILE, COD
  payment_status    VARCHAR(20) NOT NULL,                    -- PENDING, PAID, REFUNDED
  total             DECIMAL(12,2),
  delivery_address  VARCHAR(255),
  prescription_id   BIGINT,
  created_at        DATETIME(6),
  delivery_staff_id BIGINT,
  delivery_status   VARCHAR(20) NOT NULL,                    -- NOT_ASSIGNED, ASSIGNED, PICKED_UP, OUT_FOR_DELIVERY, DELIVERED, ISSUE
  delivery_otp      VARCHAR(255),
  delivery_issue    VARCHAR(255),
  delivered_at      DATETIME(6),
  FOREIGN KEY (customer_id)       REFERENCES users(id),
  FOREIGN KEY (prescription_id)   REFERENCES prescription(id),
  FOREIGN KEY (delivery_staff_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_item (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_id    BIGINT NOT NULL,
  medicine_id BIGINT NOT NULL,
  quantity    INT NOT NULL,
  unit_price  DECIMAL(10,2),
  FOREIGN KEY (order_id)    REFERENCES orders(id),
  FOREIGN KEY (medicine_id) REFERENCES medicine(id),
  CONSTRAINT chk_item_qty CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS purchase_orders (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  supplier_id    BIGINT NOT NULL,
  medicine_id    BIGINT NOT NULL,
  quantity       INT NOT NULL,
  status         VARCHAR(20) NOT NULL,                       -- PENDING, IN_STOCK, OUT_OF_STOCK, DELIVERED
  payment_status VARCHAR(20) NOT NULL,                       -- PENDING, PARTIAL, PAID
  created_at     DATETIME(6),
  due_date       DATE,
  delivered_at   DATETIME(6),
  FOREIGN KEY (supplier_id) REFERENCES users(id),
  FOREIGN KEY (medicine_id) REFERENCES medicine(id),
  CONSTRAINT chk_po_qty CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS notification (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id    BIGINT NOT NULL,
  message    VARCHAR(255),
  created_at DATETIME(6),
  seen       BIT(1) NOT NULL,
  FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;
