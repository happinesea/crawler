package com.happinesea.webcrawler.entity;

import java.time.LocalDateTime;

import com.happinesea.webcrawler.Const.DeleteFlg;
import com.happinesea.webcrawler.entity.converter.DeleteFlgConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "site_category")
public class SiteCategory {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "site_category_id")
	private Integer siteCategoryId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "site_info_id", nullable = false)
	private SiteInfo siteInfo;

	@Column(name = "cms_category_id")
	private Integer cmsCategoryId;

	@Column(name = "source_category_id")
	private Integer sourceCategoryId;

	@Column(name = "category_name")
	private String categoryName;

	@Column(name = "delete_flg")
	@Convert(converter = DeleteFlgConverter.class)
	private DeleteFlg deleteFlg;

	@Column(name = "category_url")
	private String categoryUrl;

	@Column(name = "category_list_url")
	private String categoryListUrl;

	@Column(name = "last_scan_time")
	private LocalDateTime lastScanTime;

	@Column(name = "target_category")
	private String targetCategory;

	@Column(name = "cms_target_category")
	private String cmsTargetCategory;

	@Column(name = "list_record_select_id")
	private String listRecordSelectId;

	@Column(name = "title_record_select_id")
	private String titleRecordSelectId;

	@Column(name = "contents_url_selectId")
	private String contentsUrlSelectId;

	@Column(name = "body_select_id")
	private String bodySelectId;

	@Column(name = "more_body_select_id")
	private String moreBodySelectId;

	@Column(name = "more_body_select_txt")
	private String moreBodySelectTxt;

}
