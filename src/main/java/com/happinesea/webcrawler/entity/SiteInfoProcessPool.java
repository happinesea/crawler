package com.happinesea.webcrawler.entity;

import java.time.LocalDateTime;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Data
@Table(name = "site_info_process_pool")
public class SiteInfoProcessPool {
    @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "site_info_process_id")
    private Integer siteInfoProcessId;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "site_category_id", nullable = false)
	private SiteCategory siteCategory;

	@Column(name = "process_status")
	@Convert(converter = ProcessStatusConverter.class)
	private ProcessStatus processStatus;
	
	@Column(name = "process_id")
    private String processId;

	@Column(name = "owner_instance")
	private String ownerInstance;

	@Column(name = "lease_attempt", nullable = false)
	private long leaseAttempt;

	@Column(name = "claimed_at")
	private LocalDateTime claimedAt;

	@Column(name = "heartbeat_at")
	private LocalDateTime heartbeatAt;

	@Column(name = "last_result_status")
	@Convert(converter = ProcessStatusConverter.class)
	private ProcessStatus lastResultStatus;
	
	@Column(name = "process_time")
    private LocalDateTime processTime;
}
