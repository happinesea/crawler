SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'site_category'
    AND COLUMN_NAME = 'more_body_select_txt'
);

SET @ddl = IF(
  @column_exists = 0,
  'ALTER TABLE site_category ADD COLUMN more_body_select_txt varchar(256) DEFAULT NULL AFTER more_body_select_id',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
