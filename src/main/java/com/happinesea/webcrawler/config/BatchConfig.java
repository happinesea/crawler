package com.happinesea.webcrawler.config;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;

import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.config.CategoryAssignmentPlan.CategoryAssignment;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteCategoryRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;
import com.happinesea.webcrawler.service.CrawlJobSummary;
import com.happinesea.webcrawler.service.SiteContentsService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class BatchConfig {
	static final int CATEGORY_RECOVERY_CHUNK_SIZE = 1;

	@Autowired
	private SiteInfoProcessRepository siteInfoProcessRepository;
	@Autowired
	private SiteCategoryRepository siteCategoryRepository;
	@Autowired
	private SiteContentsService siteContentsService;
	@Autowired
	private CrawlJobSummary crawlJobSummary;
	@Value("${web-crawler.crawl-process-limit-count:10}")
	private int crawlProcessLimitCount;
	@Value("${web-crawler.target-site-info-ids:}")
	private String targetSiteInfoIds;
	@Value("${web-crawler.target-site-category-ids:}")
	private String targetSiteCategoryIds;
	@Value("${web-crawler.repair-cms-content-id:}")
	private Long repairCmsContentId;

	@Bean
	public Job crawlJob(JobRepository jobRepository, Step crawlStep) {
		return new JobBuilder("crawlJob", jobRepository)
				.listener(crawlJobSummary)
				.start(crawlStep)
				.build();
	}

	@Bean
	public Step crawlStep(JobRepository jobRepository,
			PlatformTransactionManager transactionManager,
			CrawlerComponents crawlerComponents) {
		// The step transaction manager is intentionally resourceless because item
		// processing performs external HTTP calls. DB writes use short, explicit
		// transactions in SiteContentsService instead of one long step transaction.
		PlatformTransactionManager stepTransactionManager = createStepTransactionManager();

		validateTargetConfiguration();
		ensureProcessPoolsForActiveCategories();
		List<SiteInfoProcessPool> targetSnapshot = freezeTargetProcesses(
				filterTargetProcesses(siteContentsService.findAliveProcess()));
		int logicalWorkerCount = repairCmsContentId == null
				? Math.max(1, crawlProcessLimitCount)
				: 1;
		CategoryAssignmentPlan assignmentPlan = CategoryAssignmentPlan.create(
				targetSnapshot, logicalWorkerCount);
		List<CategoryAssignment> submittedAssignments = assignmentPlan.nonEmptyAssignments();
		logAssignmentPlan(assignmentPlan, targetSnapshot.size(), submittedAssignments.size());

		if (submittedAssignments.isEmpty()) {
			log.info("No SiteInfoProcess targets are available. Finish crawl job without work.");
			return new StepBuilder("emptyStep", jobRepository)
					.tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, stepTransactionManager)
					.listener(targetCountListener(0, null, crawlerComponents))
					.build();
		}

		int threadCount = submittedAssignments.size();
		StepBuilder stepBuilder = new StepBuilder("crawlStep", jobRepository);
		var step = stepBuilder.<CategoryAssignment, CategoryAssignment>chunk(
						CATEGORY_RECOVERY_CHUNK_SIZE, stepTransactionManager)
				.reader(crawlerComponents.categoryAssignmentReader(submittedAssignments))
				.processor(withoutStepTransaction(
						crawlerComponents.categoryAssignmentProcessor(), stepTransactionManager))
				.writer(crawlerComponents.categoryAssignmentWriter())
				.listener(targetCountListener(targetSnapshot.size(), null, crawlerComponents));
		if (threadCount <= 1) {
			return step.build();
		}

		ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
		taskExecutor.setCorePoolSize(threadCount);
		taskExecutor.setMaxPoolSize(threadCount);
		taskExecutor.setQueueCapacity(threadCount);
		taskExecutor.setThreadNamePrefix("crawler-");
		taskExecutor.setWaitForTasksToCompleteOnShutdown(true);
		taskExecutor.setAwaitTerminationSeconds(30);
		taskExecutor.initialize();

		return step
				.taskExecutor(taskExecutor)
				.listener(targetCountListener(targetSnapshot.size(), taskExecutor, crawlerComponents))
				.build();
	}

	static PlatformTransactionManager createStepTransactionManager() {
		ResourcelessTransactionManager transactionManager = new ResourcelessTransactionManager() {
			@Override
			protected Object doSuspend(Object transaction) {
				return TransactionSynchronizationManager.unbindResource(this);
			}

			@Override
			protected void doResume(Object transaction, Object suspendedResources) {
				TransactionSynchronizationManager.bindResource(this, suspendedResources);
			}
		};
		transactionManager.setTransactionSynchronization(
				AbstractPlatformTransactionManager.SYNCHRONIZATION_ON_ACTUAL_TRANSACTION);
		return transactionManager;
	}

	static <I, O> ItemProcessor<I, O> withoutStepTransaction(ItemProcessor<I, O> delegate,
			PlatformTransactionManager stepTransactionManager) {
		TransactionTemplate noStepTransaction = new TransactionTemplate(stepTransactionManager);
		noStepTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
		return item -> {
			AtomicReference<O> result = new AtomicReference<>();
			AtomicReference<Exception> failure = new AtomicReference<>();
			noStepTransaction.executeWithoutResult(status -> {
				try {
					result.set(delegate.process(item));
				} catch (Exception e) {
					failure.set(e);
				}
			});
			if (failure.get() != null) {
				throw failure.get();
			}
			return result.get();
		};
	}

	private StepExecutionListener targetCountListener(int targetCount, ThreadPoolTaskExecutor taskExecutor,
			CrawlerComponents crawlerComponents) {
		return new StepExecutionListener() {
			@Override
			public void beforeStep(StepExecution stepExecution) {
				crawlJobSummary.setTargetCategoryCount(targetCount);
				crawlerComponents.setProcessOwner("job-" + stepExecution.getJobExecution().getId());
			}

			@Override
			public ExitStatus afterStep(StepExecution stepExecution) {
				if (taskExecutor != null) {
					taskExecutor.shutdown();
				}
				return stepExecution.getExitStatus();
			}
		};
	}

	private List<SiteInfoProcessPool> freezeTargetProcesses(List<SiteInfoProcessPool> source) {
		List<SiteInfoProcessPool> frozen = source.stream()
				.sorted(Comparator.comparing(pool -> pool.getSiteCategory().getSiteCategoryId()))
				.toList();
		if (repairCmsContentId != null && frozen.size() > 1) {
			log.info("Limit CMS repair to one process-pool lease. cmsContentId={} candidateCount={}",
					repairCmsContentId, frozen.size());
			return List.copyOf(frozen.subList(0, 1));
		}
		return List.copyOf(frozen);
	}

	private void logAssignmentPlan(CategoryAssignmentPlan plan, int categoryCount, int submittedWorkerCount) {
		log.info("Category assignment plan. categoryCount={} logicalWorkerCount={} submittedWorkerCount={}",
				categoryCount, plan.assignments().size(), submittedWorkerCount);
		for (CategoryAssignment assignment : plan.assignments()) {
			log.info("Category assignment. worker={} categoryIds={} submitted={}",
					assignment.workerNumber(), assignment.categoryIds(), !assignment.categories().isEmpty());
		}
	}

	List<SiteInfoProcessPool> filterTargetProcesses(List<SiteInfoProcessPool> source) {
		Set<Integer> targetSiteIds = parseTargetIds(targetSiteInfoIds,
				"web-crawler.target-site-info-ids");
		Set<Integer> targetCategoryIds = parseTargetIds(targetSiteCategoryIds,
				"web-crawler.target-site-category-ids");
		if (targetSiteIds.isEmpty() && targetCategoryIds.isEmpty()) {
			return source;
		}
		List<SiteInfoProcessPool> filtered = source.stream()
				.filter(pool -> matchesTargets(pool, targetSiteIds, targetCategoryIds))
				.toList();
		log.info("Filter crawl targets. configuredSiteInfoIds={} configuredSiteCategoryIds={} "
					+ "candidateCount={} filteredCount={}",
				targetSiteIds, targetCategoryIds, source.size(), filtered.size());
		return filtered;
	}

	private boolean matchesTargets(SiteInfoProcessPool pool, Set<Integer> targetSiteIds,
			Set<Integer> targetCategoryIds) {
		if (pool == null || pool.getSiteCategory() == null) {
			return false;
		}
		SiteCategory category = pool.getSiteCategory();
		if (!targetCategoryIds.isEmpty()
				&& !targetCategoryIds.contains(category.getSiteCategoryId())) {
			return false;
		}
		return targetSiteIds.isEmpty()
				|| (category.getSiteInfo() != null
						&& targetSiteIds.contains(category.getSiteInfo().getSiteInfoId()));
	}

	private Set<Integer> parseTargetIds(String configuredValue, String propertyName) {
		if (StringUtils.isBlank(configuredValue)) {
			return Set.of();
		}
		Set<Integer> result = new HashSet<>();
		for (String raw : configuredValue.split(",", -1)) {
			String value = raw.trim();
			if (StringUtils.isBlank(value)) {
				throw new IllegalStateException(propertyName
						+ " must contain comma-separated positive integers: " + configuredValue);
			}
			try {
				int parsed = Integer.parseInt(value);
				if (parsed <= 0) {
					throw new NumberFormatException("not positive");
				}
				result.add(parsed);
			} catch (NumberFormatException e) {
				throw new IllegalStateException(propertyName
						+ " must contain comma-separated positive integers: " + configuredValue, e);
			}
		}
		return result;
	}

	private void validateTargetConfiguration() {
		parseTargetIds(targetSiteInfoIds, "web-crawler.target-site-info-ids");
		parseTargetIds(targetSiteCategoryIds, "web-crawler.target-site-category-ids");
	}

	void ensureProcessPoolsForActiveCategories() {
		List<SiteCategory> activeCategories = siteCategoryRepository.findActiveCategories();
		if (activeCategories.isEmpty()) {
			return;
		}

		Set<Integer> categoryIdsWithPool = siteInfoProcessRepository.findAll().stream()
				.filter(pool -> pool.getSiteCategory() != null && pool.getSiteCategory().getSiteCategoryId() != null)
				.map(pool -> pool.getSiteCategory().getSiteCategoryId())
				.collect(Collectors.toCollection(HashSet::new));
		for (SiteCategory category : activeCategories) {
			Integer categoryId = category.getSiteCategoryId();
			if (categoryId == null || categoryIdsWithPool.contains(categoryId)) {
				continue;
			}
			SiteInfoProcessPool pool = new SiteInfoProcessPool();
			pool.setSiteCategory(category);
			pool.setProcessStatus(ProcessStatus.NONE);
			pool.setProcessTime(null);
			siteInfoProcessRepository.save(pool);
			categoryIdsWithPool.add(categoryId);
			log.info("Created missing SiteInfoProcessPool. siteCategoryId={}", categoryId);
		}
	}
}
