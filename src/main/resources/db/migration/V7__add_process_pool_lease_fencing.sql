ALTER TABLE site_info_process_pool
    MODIFY process_id varchar(191) DEFAULT NULL,
    ADD COLUMN owner_instance varchar(96) DEFAULT NULL AFTER process_id,
    ADD COLUMN lease_attempt bigint NOT NULL DEFAULT 0 AFTER owner_instance,
    ADD COLUMN claimed_at datetime(6) DEFAULT NULL AFTER lease_attempt,
    ADD COLUMN heartbeat_at datetime(6) DEFAULT NULL AFTER claimed_at,
    ADD COLUMN last_result_status char(1) DEFAULT NULL AFTER heartbeat_at;

UPDATE site_info_process_pool
SET lease_attempt = 0
WHERE lease_attempt IS NULL;

CREATE INDEX idx_process_pool_claim
    ON site_info_process_pool (process_status, heartbeat_at, process_time);

ALTER TABLE site_info_process_pool
    ADD UNIQUE KEY uk_process_pool_site_category (site_category_id);
