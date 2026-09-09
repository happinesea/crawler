ALTER TABLE site_info_process_pool
    MODIFY site_info_process_id int(10) NOT NULL AUTO_INCREMENT;

ALTER TABLE site_info_process_pool
    MODIFY process_id varchar(64) DEFAULT NULL;
