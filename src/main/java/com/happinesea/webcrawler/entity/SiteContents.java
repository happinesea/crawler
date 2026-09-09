package com.happinesea.webcrawler.entity;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.converter.ProcessStatusConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Entity
@Data
@Table(name = "site_contents", uniqueConstraints =
		@UniqueConstraint(name = "uk_site_contents_normalized_url_hash", columnNames = "normalized_url_hash"))
public class SiteContents {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "site_contents_id")
	private Long siteContentsId;

	@Column(name = "url")
	private String url;
	@Column(name = "requested_url", length = 2048)
	private String requestedUrl;
	@Column(name = "redirected_url", length = 2048)
	private String redirectedUrl;
	@Column(name = "canonical_url", length = 2048)
	private String canonicalUrl;
	@Column(name = "normalized_url", length = 2048)
	private String normalizedUrl;
	@Column(name = "normalized_url_hash", length = 64)
	private String normalizedUrlHash;
	@Column(name = "source_url", length = 2048)
	private String sourceUrl;
	@Lob
	@Column(name = "title")
	private String title;
	@Lob
	@Column(name = "description")
	private String description;
	@Lob
	@Column(name = "contents")
	private String contents;
	@Column(name = "featured_image_url", length = 1024)
	private String featuredImageUrl;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "site_categoy_id", nullable = false)
	private SiteCategory siteCategory;

	@Column(name = "process_status")
	@Convert(converter = ProcessStatusConverter.class)
	private ProcessStatus processStatus;

	@Column(name = "cms_content_id")
	private Long cmsContentId;

	public SiteInfo getSiteInfo() {
		return siteCategory == null ? null : siteCategory.getSiteInfo();
	}
}
