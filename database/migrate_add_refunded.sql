-- Run ONCE on an existing 'smartcare' database (created before the REFUNDED payment status was added).
-- Hibernate's ddl-auto=update never changes existing column types, so the new enum value must be allowed manually.
-- Safe to run on both ENUM and VARCHAR columns; existing data is kept.
-- A brand-new database does NOT need this.
USE smartcare;
ALTER TABLE orders MODIFY payment_status VARCHAR(20) NOT NULL;
