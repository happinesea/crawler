CREATE TABLE `site_info` (
  `site_id` int(10) NOT NULL,
  `site_name` varchar(255) NOT NULL,
  `site_url` varchar(1024) NOT NULL,
  `logo_url` varchar(256) DEFAULT NULL,
  `delete_flg` char(1) NOT NULL DEFAULT '0' COMMENT '1:delete',
  `contents_type` char(1) NOT NULL DEFAULT '1' COMMENT '1:html, 2:wordpress'
);