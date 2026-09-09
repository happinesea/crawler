package com.happinesea.webcrawler.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProcessPoolHeartbeatManager {
	private final SiteInfoProcessRepository repository;
	private final Map<Integer, Lease> activeLeases = new ConcurrentHashMap<>();

	public void register(SiteInfoProcessPool pool) {
		if (pool != null && pool.getSiteInfoProcessId() != null && pool.getProcessId() != null) {
			activeLeases.put(pool.getSiteInfoProcessId(),
					new Lease(pool.getProcessId(), pool.getLeaseAttempt()));
		}
	}

	public void unregister(SiteInfoProcessPool pool) {
		if (pool != null && pool.getSiteInfoProcessId() != null) {
			activeLeases.remove(pool.getSiteInfoProcessId(),
					new Lease(pool.getProcessId(), pool.getLeaseAttempt()));
		}
	}

	@Scheduled(fixedDelayString = "${web-crawler.heartbeat-interval-ms:60000}")
	public void heartbeat() {
		LocalDateTime now = LocalDateTime.now();
		activeLeases.forEach((id, lease) -> {
			int updated = repository.heartbeatOwnedProcessing(id, lease.owner(), lease.attempt(),
					ProcessStatus.PROCESSING, now);
			if (updated == 0) {
				activeLeases.remove(id, lease);
				log.warn("Stop heartbeat for a lease that is no longer owned. poolId={} owner={} attempt={}",
						id, lease.owner(), lease.attempt());
			}
		});
	}

	int activeLeaseCount() {
		return activeLeases.size();
	}

	private record Lease(String owner, long attempt) {
	}
}
