-- ---------------------------------------------------------------------------
-- init.sql — executed ONCE by the SingleStore Dev Image on first start.
-- The image records completion in /data/.init.sql.done; to re-run it, delete
-- that file (or the data volume: `docker compose down -v`) and restart.
-- ---------------------------------------------------------------------------

CREATE DATABASE IF NOT EXISTS demo;
USE demo;

-- Small lookup table. REFERENCE tables are replicated to every leaf, so joins
-- against them never need data movement between nodes.
CREATE REFERENCE TABLE IF NOT EXISTS categories (
    id   INT          NOT NULL,
    name VARCHAR(100) NOT NULL,
    PRIMARY KEY (id)
);

-- Main table (columnstore, the default table type).
--  * SHARD KEY  : how rows are distributed across partitions.
--  * SORT KEY   : on-disk ordering of column segments (good for range scans).
--  * HASH KEYs  : secondary indexes for point lookups / filters.
-- Note: SingleStore has no foreign keys, and unique keys on a sharded table
-- must include the shard key — hence no UNIQUE on sku.
CREATE TABLE IF NOT EXISTS products (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    sku         VARCHAR(64)   NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    category_id INT           NOT NULL,
    price       DECIMAL(10,2) NOT NULL,
    stock       INT           NOT NULL DEFAULT 0,
    created_at  DATETIME(6)   NOT NULL,
    updated_at  DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    SHARD KEY (id),
    SORT KEY (created_at),
    KEY idx_products_sku (sku) USING HASH,
    KEY idx_products_category (category_id) USING HASH
);

-- Least-privilege user for the Spring Boot app (root is kept for admin only).
CREATE USER 'app'@'%' IDENTIFIED BY 'app_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON demo.* TO 'app'@'%';

-- ---------------------------------------------------------------------------
-- Seed data
-- ---------------------------------------------------------------------------
INSERT INTO categories (id, name) VALUES
    (1, 'Laptops'),
    (2, 'Monitors'),
    (3, 'Keyboards'),
    (4, 'Audio'),
    (5, 'Accessories');

INSERT INTO products (sku, name, category_id, price, stock, created_at, updated_at) VALUES
    ('LAP-001', 'Ultrabook 13" i7 16GB',          1, 1299.00,  25, NOW(6), NOW(6)),
    ('LAP-002', 'Workstation 16" Ryzen 9 32GB',   1, 2199.00,  10, NOW(6), NOW(6)),
    ('LAP-003', 'Chromebook 14"',                 1,  349.99,  60, NOW(6), NOW(6)),
    ('LAP-004', 'Gaming Laptop 17" RTX',          1, 1899.50,  12, NOW(6), NOW(6)),
    ('LAP-005', 'Convertible 2-in-1 13"',         1,  899.00,  30, NOW(6), NOW(6)),
    ('MON-001', '27" 4K IPS Monitor',             2,  429.00,  40, NOW(6), NOW(6)),
    ('MON-002', '34" Ultrawide Curved',           2,  649.00,  18, NOW(6), NOW(6)),
    ('MON-003', '24" 1080p Office Monitor',       2,  139.90, 120, NOW(6), NOW(6)),
    ('MON-004', '32" 165Hz Gaming Monitor',       2,  499.00,  22, NOW(6), NOW(6)),
    ('MON-005', 'Portable 15.6" USB-C Monitor',   2,  199.00,  35, NOW(6), NOW(6)),
    ('KEY-001', 'Mechanical Keyboard TKL',        3,  119.00,  75, NOW(6), NOW(6)),
    ('KEY-002', 'Wireless Low-Profile Keyboard',  3,   89.00,  90, NOW(6), NOW(6)),
    ('KEY-003', 'Ergonomic Split Keyboard',       3,  179.00,  20, NOW(6), NOW(6)),
    ('KEY-004', '60% Hot-Swap Keyboard',          3,   99.00,  45, NOW(6), NOW(6)),
    ('AUD-001', 'Noise-Cancelling Headphones',    4,  299.00,  50, NOW(6), NOW(6)),
    ('AUD-002', 'USB Condenser Microphone',       4,  129.00,  33, NOW(6), NOW(6)),
    ('AUD-003', 'Bluetooth Speaker',              4,   79.00,  80, NOW(6), NOW(6)),
    ('AUD-004', 'True Wireless Earbuds',          4,  149.00,  65, NOW(6), NOW(6)),
    ('ACC-001', 'USB-C Docking Station',          5,  189.00,  40, NOW(6), NOW(6)),
    ('ACC-002', 'Wireless Mouse',                 5,   39.90, 150, NOW(6), NOW(6)),
    ('ACC-003', 'Laptop Stand Aluminium',         5,   49.00, 100, NOW(6), NOW(6)),
    ('ACC-004', '1080p Webcam',                   5,   69.00,  70, NOW(6), NOW(6)),
    ('ACC-005', 'USB-C to HDMI Adapter',          5,   19.90, 200, NOW(6), NOW(6)),
    ('ACC-006', 'Desk Mat XL',                    5,   24.90, 130, NOW(6), NOW(6));
