SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'site_category'
    AND COLUMN_NAME = 'cms_category_id'
);

SET @ddl = IF(
  @column_exists = 0,
  'ALTER TABLE site_category ADD COLUMN cms_category_id int(10) DEFAULT NULL AFTER site_info_id',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
