CREATE TABLE site_info (
  site_info_id int(10) NOT NULL AUTO_INCREMENT,
  site_name varchar(255) NOT NULL,
  site_url varchar(1024) NOT NULL,
  logo_url varchar(256) DEFAULT NULL,
  contract_type char(1) NOT NULL DEFAULT '0' COMMENT '0:NONE,1:partner,2:reprint,9:other',
  delete_flg char(1) NOT NULL DEFAULT '0' COMMENT '1:delete',
  contents_type char(1) NOT NULL DEFAULT '1' COMMENT '1:html, 2:wordpress',
  PRIMARY KEY (site_info_id)
);

CREATE TABLE site_category (
  site_category_id int(10) NOT NULL AUTO_INCREMENT,
  site_info_id int(10) NOT NULL,
  cms_category_id int(10) DEFAULT NULL,
  category_name varchar(256) NOT NULL,
  delete_flg char(1) NOT NULL DEFAULT '0' COMMENT '1:delete',
  category_url varchar(256) NOT NULL,
  category_list_url varchar(256) DEFAULT NULL,
  last_scan_time datetime DEFAULT NULL,
  target_category varchar(256) DEFAULT NULL,
  cms_target_category varchar(256) DEFAULT NULL,
  list_record_select_id varchar(256) NOT NULL,
  title_record_select_id varchar(256) NOT NULL,
  contents_url_selectId varchar(256) NOT NULL,
  body_select_id varchar(256) NOT NULL,
  more_body_select_id varchar(256) NOT NULL,
  more_body_select_txt varchar(256) DEFAULT NULL,
  PRIMARY KEY (site_category_id)
);

CREATE TABLE site_contents (
  site_contents_id bigint(20) NOT NULL AUTO_INCREMENT,
  url varchar(1024) NOT NULL,
  title text NOT NULL,
  description text NULL,
  contents text NOT NULL,
  featured_image_url varchar(1024) DEFAULT NULL,
  site_categoy_id int(10) NOT NULL,
  process_status char(1) NOT NULL DEFAULT '1' COMMENT '1:NONE、2:processing、9:fail、0:sucess',
  cms_content_id bigint(20) DEFAULT NULL,
  PRIMARY KEY (site_contents_id),
  UNIQUE KEY url (url(255))
);

CREATE TABLE site_info_process_pool (
  site_info_process_id int(10) NOT NULL,
  site_category_id int(10) NOT NULL,
  process_status char(1) NOT NULL DEFAULT '0' COMMENT '1:NONE、2:processing、9:fail、0:sucess',
  process_id varchar(20) DEFAULT NULL,
  process_time datetime DEFAULT NULL,
  PRIMARY KEY (site_info_process_id)
);
