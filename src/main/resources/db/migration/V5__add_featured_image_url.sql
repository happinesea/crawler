SET @column_exists = (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'site_contents'
    AND COLUMN_NAME = 'featured_image_url'
);

SET @ddl = IF(
  @column_exists = 0,
  'ALTER TABLE site_contents ADD COLUMN featured_image_url varchar(1024) DEFAULT NULL AFTER contents',
  'SELECT 1'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
