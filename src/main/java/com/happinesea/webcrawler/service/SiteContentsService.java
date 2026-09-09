package com.happinesea.webcrawler.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.happinesea.webcrawler.ContentsParser;
import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.RunFatalFailure;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteContentsRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;

import jakarta.transaction.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SiteContentsService {
	@PersistenceContext
	private EntityManager entityManager;
	private static final List<ProcessStatus> CRAWL_TARGET_STATUSES =
			List.of(ProcessStatus.NONE, ProcessStatus.FAIL, ProcessStatus.SUCCESS);

	@Autowired
	private SiteContentsRepository siteContentsRepository;
	@Autowired
	private SiteInfoProcessRepository siteInfoProcessRepository;
	@Autowired
	private WordPressPostService wordPressPostService;
	@Autowired
	private AiAnalysisService aiAnalysisService;
	@Autowired
	private CrawlJobSummary crawlJobSummary;
	@Autowired
	private ProcessPoolHeartbeatManager heartbeatManager;
	@Autowired
	private SiteContentsInsertService siteContentsInsertService;
	@Autowired
	private UrlCanonicalizer urlCanonicalizer;
	@Autowired
	private ContentsParser contentsParser;
	@Value("${web-crawler.post-contents-limit-count}")
	private int postContentsLimitCount;
	@Value("${web-crawler.post-featured-image-only:false}")
	private boolean postFeaturedImageOnly;
	@Value("${web-crawler.skip-cms-post:true}")
	private boolean skipCmsPost;
	@Value("${web-crawler.sync-existing-cms-content:true}")
	private boolean syncExistingCmsContent = true;
	@Value("${web-crawler.repair-cms-content-id:}")
	private Long repairCmsContentId;
	@Value("${web-crawler.processing-timeout-minutes:60}")
	private int processingTimeoutMinutes;

	public List<SiteInfoProcessPool> findAliveProcess() {
		return siteInfoProcessRepository.findClaimCandidates(
				CRAWL_TARGET_STATUSES, ProcessStatus.PROCESSING, staleProcessingBefore());
	}

	@Transactional
	public Optional<SiteInfoProcessPool> claimSiteInfoProcess(Integer siteInfoProcessId, String owner) {
		if (siteInfoProcessId == null || StringUtils.isBlank(owner)) {
			return Optional.empty();
		}
		LocalDateTime now = LocalDateTime.now();
		String ownerInstance = owner.contains("/") ? owner.substring(0, owner.indexOf('/')) : owner;
		int updated = siteInfoProcessRepository.claimForProcessing(siteInfoProcessId, owner, ownerInstance, now,
				CRAWL_TARGET_STATUSES, ProcessStatus.PROCESSING, now.minusMinutes(processingTimeout()));
		if (updated == 0) {
			return Optional.empty();
		}
		Optional<SiteInfoProcessPool> claimed = siteInfoProcessRepository.findByIdWithCategory(siteInfoProcessId);
		claimed.ifPresent(heartbeatManager::register);
		return claimed;
	}

	@Transactional
	public SiteInfoProcessPool changSiteInfoProcess2Processing(SiteInfoProcessPool process) {
		process.setProcessStatus(ProcessStatus.PROCESSING);
		if (StringUtils.isBlank(process.getProcessId())) {
			process.setProcessId("manual");
		}
		process.setProcessTime(LocalDateTime.now());
		return siteInfoProcessRepository.save(process);
	}

	@Transactional
	public SiteInfoProcessPool changSiteInfoProcess2Sucess(SiteInfoProcessPool process) {
		if (StringUtils.isNotBlank(process.getProcessId())) {
			Optional<SiteInfoProcessPool> finished = finishOwnedProcess(process, ProcessStatus.SUCCESS);
			if (finished.isPresent()) {
				return finished.get();
			}
			return process;
		}
		process.setProcessStatus(ProcessStatus.SUCCESS);
		process.setProcessTime(LocalDateTime.now());
		return siteInfoProcessRepository.save(process);

	}

	@Transactional
	public SiteInfoProcessPool changSiteInfoProcess2Fail(SiteInfoProcessPool process) {
		if (StringUtils.isNotBlank(process.getProcessId())) {
			Optional<SiteInfoProcessPool> finished = finishOwnedProcess(process, ProcessStatus.FAIL);
			if (finished.isPresent()) {
				return finished.get();
			}
			return process;
		}
		process.setProcessStatus(ProcessStatus.FAIL);
		process.setProcessTime(LocalDateTime.now());
		return siteInfoProcessRepository.save(process);

	}

	@Transactional
	public Optional<SiteInfoProcessPool> finishOwnedProcess(SiteInfoProcessPool process, ProcessStatus nextStatus) {
		if (process == null || process.getSiteInfoProcessId() == null || StringUtils.isBlank(process.getProcessId())) {
			return Optional.empty();
		}
		int updated = siteInfoProcessRepository.finishOwnedProcessing(process.getSiteInfoProcessId(),
				process.getProcessId(), process.getLeaseAttempt(), ProcessStatus.NONE, nextStatus,
				ProcessStatus.PROCESSING, LocalDateTime.now());
		if (updated == 0) {
			log.warn("Skip process status overwrite because owner no longer matches. siteInfoProcessId={} "
							+ "processId={} nextStatus={}",
					process.getSiteInfoProcessId(), process.getProcessId(), nextStatus);
			return Optional.empty();
		}
		heartbeatManager.unregister(process);
		// Keep the returned work item backward compatible while the persisted slot is AVAILABLE/NONE.
		if (entityManager.contains(process)) {
			entityManager.detach(process);
		}
		process.setProcessStatus(nextStatus);
		process.setLastResultStatus(nextStatus);
		return Optional.of(process);
	}

	public List<SiteInfoProcessPool> saveAllProcessPools(List<? extends SiteInfoProcessPool> pools) {
		if (isCmsRepairConfigured()) {
			return repairConfiguredCmsContent(pools);
		}

		List<SiteInfoProcessPool> result = new ArrayList<SiteInfoProcessPool>();
		if (syncExistingCmsContent) {
			backfillMissingCmsDescriptions();
		}
		for (SiteInfoProcessPool siteInfoProcessPool : pools) {
			if (ProcessStatus.PROCESSING.equals(siteInfoProcessPool.getProcessStatus())) {
				if (skipCmsPost) {
					log.info("Skip CMS post. siteInfoProcessId={}", siteInfoProcessPool.getSiteInfoProcessId());
					finishOwnedProcess(siteInfoProcessPool, ProcessStatus.SUCCESS).ifPresent(result::add);
				} else {
					SiteCategory category = siteInfoProcessPool.getSiteCategory();
					PostContentsResult categorySyncResult = syncExistingCmsContent
							? syncExistingCmsCategories(category) : new PostContentsResult(0, 0, 0);
					List<SiteContents> contentsList = siteContentsRepository.findContents4Post(category,
							List.of(ProcessStatus.NONE, ProcessStatus.FAIL));
					int candidateCount = contentsList.size();
					if (postFeaturedImageOnly) {
						contentsList = contentsList.stream()
								.filter(contents -> StringUtils.isNotBlank(contents.getFeaturedImageUrl()))
								.toList();
					}
					log.info("CMS post target contents. siteInfoProcessId={} categoryId={} candidateCount={} "
							+ "targetCount={} runWidePostLimit={} featuredImageOnly={}",
							siteInfoProcessPool.getSiteInfoProcessId(),
							category == null ? null : category.getSiteCategoryId(), candidateCount,
							contentsList.size(), postContentsLimitCount, postFeaturedImageOnly);
					PostContentsResult postResult = postContents(contentsList);
					int failureCount = categorySyncResult.failureCount() + postResult.failureCount();
					log.info("CMS post finished. siteInfoProcessId={} categorySyncSuccessCount={} "
							+ "categorySyncFailureCount={} successCount={} failureCount={} skippedCount={}",
							siteInfoProcessPool.getSiteInfoProcessId(), categorySyncResult.successCount(),
							categorySyncResult.failureCount(), postResult.successCount(), postResult.failureCount(),
							Math.max(0, contentsList.size() - postResult.processedCount()));
					if (failureCount > 0) {
						finishOwnedProcess(siteInfoProcessPool, ProcessStatus.FAIL).ifPresent(result::add);
						log.warn("CMS post failed. Continue remaining process pools. siteInfoProcessId={} failureCount={}",
								siteInfoProcessPool.getSiteInfoProcessId(), failureCount);
						continue;
					}
					finishOwnedProcess(siteInfoProcessPool, ProcessStatus.SUCCESS).ifPresent(result::add);
				}
			}
		}
		return result;
	}

	private PostContentsResult syncExistingCmsCategories(SiteCategory category) {
		if (category == null) {
			return new PostContentsResult(0, 0, 0);
		}
		List<SiteContents> cmsLinkedContents = siteContentsRepository.findCmsLinkedContents(category);
		int successCount = 0;
		int failureCount = 0;
		for (SiteContents contents : cmsLinkedContents) {
			try {
				backfillDescriptionIfMissing(contents);
				wordPressPostService.updateCategories(contents);
				if (ProcessStatus.FAIL.equals(contents.getProcessStatus())) {
					wordPressPostService.updateSourceMetadata(contents);
					changeSiteContentsStatus(contents, ProcessStatus.SUCCESS);
				}
				successCount++;
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				failureCount++;
				log.warn("Failed to update CMS categories. siteContentsId={} cmsContentId={} url={}",
						contents.getSiteContentsId(), contents.getCmsContentId(), contents.getUrl(), e);
			}
		}
		if (!cmsLinkedContents.isEmpty()) {
			log.info("CMS category sync finished. categoryId={} targetCount={} successCount={} failureCount={}",
					category.getSiteCategoryId(), cmsLinkedContents.size(), successCount, failureCount);
		}
		return new PostContentsResult(successCount, failureCount, cmsLinkedContents.size());
	}

	public boolean isCmsRepairConfigured() {
		return repairCmsContentId != null;
	}

	public List<SiteInfoProcessPool> repairConfiguredCmsContent(
			List<? extends SiteInfoProcessPool> pools) {
		List<SiteInfoProcessPool> result = new ArrayList<>();
		ProcessStatus repairStatus = ProcessStatus.FAIL;
		try {
			if (skipCmsPost) {
				throw new IllegalStateException("CMS repair requires skip-cms-post=false.");
			}
			SiteContents contents = siteContentsRepository.findByCmsContentIdWithCategory(repairCmsContentId)
					.orElseThrow(() -> new IllegalStateException(
							"CMS repair target is not found. cmsContentId=" + repairCmsContentId));
			refreshFeaturedImageIfMissing(contents);
			boolean updated = wordPressPostService.repairImagePolicyIfNeeded(contents);
			crawlJobSummary.incrementWordPressPostSuccess();
			repairStatus = ProcessStatus.SUCCESS;
			log.info("CMS featured-image repair finished. siteContentsId={} cmsContentId={} updated={} "
					+ "featuredImageUrl={}", contents.getSiteContentsId(), contents.getCmsContentId(), updated,
					contents.getFeaturedImageUrl());
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			crawlJobSummary.incrementWordPressPostFailure();
			log.warn("Failed to repair configured CMS featured image. cmsContentId={}", repairCmsContentId, e);
		}

		for (SiteInfoProcessPool pool : pools) {
			if (pool != null && ProcessStatus.PROCESSING.equals(pool.getProcessStatus())) {
				finishOwnedProcess(pool, repairStatus).ifPresent(result::add);
			}
		}
		return result;
	}

	private void refreshFeaturedImageIfMissing(SiteContents contents) throws Exception {
		if (StringUtils.isNotBlank(contents.getFeaturedImageUrl())) {
			return;
		}
		SiteContents refreshTarget = new SiteContents();
		refreshTarget.setTitle(contents.getTitle());
		refreshTarget.setUrl(contents.getUrl());
		refreshTarget.setSiteCategory(contents.getSiteCategory());
		SiteContents refreshed = contentsParser.loadContents(refreshTarget);
		if (StringUtils.isBlank(refreshed.getFeaturedImageUrl())) {
			throw new IllegalStateException("Source reparse did not select a featured image. siteContentsId="
					+ contents.getSiteContentsId() + " url=" + contents.getUrl());
		}
		contents.setFeaturedImageUrl(refreshed.getFeaturedImageUrl());
		siteContentsRepository.save(contents);
		log.info("Refreshed CMS repair featured image from source. siteContentsId={} cmsContentId={} "
				+ "featuredImageUrl={}", contents.getSiteContentsId(), contents.getCmsContentId(),
				contents.getFeaturedImageUrl());
	}

	private void backfillDescriptionIfMissing(SiteContents contents) {
		if (contents == null || StringUtils.isNotBlank(contents.getDescription())
				|| StringUtils.isBlank(contents.getContents())) {
			return;
		}
		contents.setDescription(summarizeText(Jsoup.parseBodyFragment(contents.getContents()).text()));
		siteContentsRepository.save(contents);
	}

	private void backfillMissingCmsDescriptions() {
		for (SiteContents contents : siteContentsRepository.findCmsLinkedContentsWithBlankDescription()) {
			backfillDescriptionIfMissing(contents);
		}
	}

	private String summarizeText(String text) {
		if (StringUtils.isBlank(text)) {
			return null;
		}
		String normalized = text.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= 240) {
			return normalized;
		}
		return normalized.substring(0, 240);
	}

	private PostContentsResult postContents(List<SiteContents> contentsList) {
		int successCount = 0;
		int failureCount = 0;
		int processedCount = 0;
		for (SiteContents contents : contentsList) {
			if (!crawlJobSummary.tryReserveWordPressPostAttempt(postContentsLimitCount)) {
				break;
			}
			processedCount++;
			SiteContents postTarget = null;
			try {
				backfillDescriptionIfMissing(contents);
				postTarget = preparePostTarget(contents);
				Long cmsContentId = wordPressPostService.post(postTarget);
				applyPostedContent(contents, postTarget, cmsContentId);
				changeSiteContentsStatus(contents, ProcessStatus.SUCCESS);
				crawlJobSummary.incrementWordPressPostSuccess();
				successCount++;
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				if (postTarget != null && postTarget.getCmsContentId() != null) {
					applyPostedContent(contents, postTarget, postTarget.getCmsContentId());
				}
				failureCount++;
				crawlJobSummary.incrementWordPressPostFailure();
				changeSiteContentsStatus(contents, ProcessStatus.FAIL);
				log.warn("Failed to post content. siteContentsId={} url={}", contents.getSiteContentsId(),
						contents.getUrl(), e);
			}
		}
		return new PostContentsResult(successCount, failureCount, processedCount);
	}

	private record PostContentsResult(int successCount, int failureCount, int processedCount) {
	}

	private SiteContents preparePostTarget(SiteContents contents) {
		return aiAnalysisService.prepareForPost(contents);
	}

	private void applyPostedContent(SiteContents contents, SiteContents postTarget, Long cmsContentId) {
		if (postTarget != null && postTarget != contents) {
			contents.setTitle(firstNotBlank(postTarget.getTitle(), contents.getTitle()));
			contents.setContents(firstNotBlank(postTarget.getContents(), contents.getContents()));
			contents.setDescription(firstNotBlank(postTarget.getDescription(), contents.getDescription()));
		}
		contents.setCmsContentId(cmsContentId);
	}

	private String firstNotBlank(String first, String fallback) {
		return StringUtils.isBlank(first) ? fallback : first;
	}

	@Transactional
	public SiteContents changeSiteContentsStatus(SiteContents contents, ProcessStatus status) {
		contents.setProcessStatus(status);
		return siteContentsRepository.save(contents);
	}

	/**
	 * 重複しないデータだけを一括保存
	 * 
	 * @param newContents
	 */
	@Transactional
	public List<SiteContents> bulkInsertIfNotExists(List<SiteContents> newContents) {
		if (newContents == null || newContents.isEmpty()) {
			return List.of();
		}

		Set<String> seenHashes = new HashSet<>();
		List<SiteContents> insertCandidates = newContents.stream()
				.filter(c -> c != null && StringUtils.isNotBlank(c.getUrl()))
				.peek(c -> {
					if (StringUtils.isBlank(c.getNormalizedUrlHash())) {
						var canonical = urlCanonicalizer.canonicalize(c.getUrl());
						c.setRequestedUrl(canonical.requestedUrl());
						c.setNormalizedUrl(canonical.normalizedUrl());
						c.setNormalizedUrlHash(canonical.normalizedUrlHash());
						c.setSourceUrl(canonical.requestedUrl());
						c.setUrl(canonical.normalizedUrl());
					}
					if (c.getProcessStatus() == null) {
						c.setProcessStatus(ProcessStatus.NONE);
					}
				})
				.filter(c -> seenHashes.add(c.getNormalizedUrlHash()))
				.toList();
		if (insertCandidates.isEmpty()) {
			return List.of();
		}

		// 1. URLリスト抽出
		Set<String> existingHashes = siteContentsRepository.findAllByNormalizedUrlHashIn(
				insertCandidates.stream().map(SiteContents::getNormalizedUrlHash).toList()).stream()
				.map(SiteContents::getNormalizedUrlHash).collect(java.util.stream.Collectors.toSet());

		// 2. 既存データ取得
		Set<String> existingUrls = siteContentsRepository.findAllByUrlIn(
				insertCandidates.stream().flatMap(c -> java.util.stream.Stream.of(c.getUrl(), c.getRequestedUrl()))
						.filter(StringUtils::isNotBlank).distinct().toList()).stream()
				.map(SiteContents::getUrl).collect(java.util.stream.Collectors.toSet());

		List<SiteContents> saved = new ArrayList<>();
		for (SiteContents candidate : insertCandidates) {
			if (existingHashes.contains(candidate.getNormalizedUrlHash()) || existingUrls.contains(candidate.getUrl())
					|| existingUrls.contains(candidate.getRequestedUrl())) {
				continue;
			}
			try {
				saved.add(siteContentsInsertService.insertIsolated(candidate));
			} catch (DataIntegrityViolationException duplicate) {
				if (!siteContentsRepository.existsByNormalizedUrlHash(candidate.getNormalizedUrlHash())) {
					throw duplicate;
				}
				log.info("Skip duplicate normalized URL after concurrent insert. hash={} url={}",
						candidate.getNormalizedUrlHash(), candidate.getUrl());
			}
		}
		crawlJobSummary.addNewSaved(saved.size());
		crawlJobSummary.addDuplicateSkipped(insertCandidates.size() - saved.size());
		return saved;
	}

	private LocalDateTime staleProcessingBefore() {
		return LocalDateTime.now().minusMinutes(processingTimeout());
	}

	private int processingTimeout() {
		return Math.max(1, processingTimeoutMinutes);
	}
}
