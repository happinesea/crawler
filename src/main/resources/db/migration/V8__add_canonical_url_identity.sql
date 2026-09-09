ALTER TABLE site_category
    ADD COLUMN source_category_id int(10) DEFAULT NULL AFTER cms_category_id;

ALTER TABLE site_contents
    ADD COLUMN requested_url varchar(2048) DEFAULT NULL AFTER url,
    ADD COLUMN redirected_url varchar(2048) DEFAULT NULL AFTER requested_url,
    ADD COLUMN canonical_url varchar(2048) DEFAULT NULL AFTER redirected_url,
    ADD COLUMN normalized_url varchar(2048) DEFAULT NULL AFTER canonical_url,
    ADD COLUMN normalized_url_hash char(64) DEFAULT NULL AFTER normalized_url,
    ADD COLUMN source_url varchar(2048) DEFAULT NULL AFTER normalized_url_hash;

UPDATE site_contents
SET requested_url = url,
    normalized_url = url,
    normalized_url_hash = LOWER(SHA2(url, 256)),
    source_url = url
WHERE normalized_url_hash IS NULL;

ALTER TABLE site_contents
    MODIFY normalized_url_hash char(64) NOT NULL,
    ADD UNIQUE KEY uk_site_contents_normalized_url_hash (normalized_url_hash);
