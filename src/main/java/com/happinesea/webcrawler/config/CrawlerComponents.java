package com.happinesea.webcrawler.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.item.support.SynchronizedItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.ContentsParser;
import com.happinesea.webcrawler.NotFoundContentsException;
import com.happinesea.webcrawler.RunFatalFailure;
import com.happinesea.webcrawler.config.CategoryAssignmentPlan.CategoryAssignment;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.service.CrawlJobSummary;
import com.happinesea.webcrawler.service.SiteContentsService;
import com.happinesea.webcrawler.service.ProcessOwnerFactory;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Configuration
@Slf4j
@Data
public class CrawlerComponents {

	@Autowired
	private SiteContentsService siteContentsService;

	@Autowired
	private ContentsParser contentsParser;

	@Autowired
	private CrawlJobSummary crawlJobSummary;

	@Autowired
	private ProcessOwnerFactory processOwnerFactory = new ProcessOwnerFactory();

	private volatile String processOwner = "job-manual";

	@Value("${web-crawler.crawl-content-limit-count:10}")
	private int crawlContentLimitCount;

	public ItemReader<SiteInfoProcessPool> siteInfoProcessReader() {
		List<SiteInfoProcessPool> aliveList = siteContentsService.findAliveProcess();
		return siteInfoProcessReader(aliveList);
	}

	public ItemReader<SiteInfoProcessPool> siteInfoProcessReader(List<SiteInfoProcessPool> targetList) {
		List<SiteInfoProcessPool> aliveList = targetList == null ? List.of() : targetList;
		return new SynchronizedItemReader<>(new ListItemReader<>(aliveList));
	}

	public ItemReader<CategoryAssignment> categoryAssignmentReader(List<CategoryAssignment> assignments) {
		List<CategoryAssignment> assignmentList = assignments == null ? List.of() : assignments;
		return new SynchronizedItemReader<>(new ListItemReader<>(assignmentList));
	}

	@Bean
	public ItemProcessor<CategoryAssignment, CategoryAssignment> categoryAssignmentProcessor() {
		return assignment -> {
			ItemProcessor<SiteInfoProcessPool, SiteInfoProcessPool> categoryProcessor = siteInfoProcessor();
			for (int index = 0; index < assignment.categories().size(); index++) {
				SiteInfoProcessPool category = assignment.categories().get(index);
				log.info("Worker category start. worker={} siteInfoProcessId={} categoryId={}",
						assignment.workerNumber(), category.getSiteInfoProcessId(), categoryId(category));
				categoryProcessor.process(category);
				log.info("Worker category complete. worker={} siteInfoProcessId={} categoryId={}",
						assignment.workerNumber(), category.getSiteInfoProcessId(), categoryId(category));
				if (index + 1 < assignment.categories().size()) {
					SiteInfoProcessPool next = assignment.categories().get(index + 1);
					log.info("Worker category next. worker={} siteInfoProcessId={} categoryId={}",
							assignment.workerNumber(), next.getSiteInfoProcessId(), categoryId(next));
				}
			}
			return assignment;
		};
	}

	@Bean
	public ItemWriter<CategoryAssignment> categoryAssignmentWriter() {
		return chunk -> {
			// Processing, CMS publication, and lease release happen inside each assignment.
		};
	}

	@Bean
	public ItemProcessor<SiteInfoProcessPool, SiteInfoProcessPool> siteInfoProcessor() {
		return process -> {
			String owner = processOwnerFactory.newOwner(currentProcessOwner());
			SiteInfoProcessPool claimedProcess = siteContentsService
					.claimSiteInfoProcess(process.getSiteInfoProcessId(), owner)
					.orElse(null);
			if (claimedProcess == null) {
				log.info("Skip already claimed siteInfoProcessPool. siteInfoProcessId={} owner={}",
						process.getSiteInfoProcessId(), owner);
				return null;
			}

			ProcessStatus resultStatus = ProcessStatus.FAIL;
			boolean released = false;
			try {
				if (siteContentsService.isCmsRepairConfigured()) {
					List<SiteInfoProcessPool> releaseResult = siteContentsService
							.repairConfiguredCmsContent(List.of(claimedProcess));
					if (releaseResult.isEmpty()) {
						throw new IllegalStateException("Process pool lease was lost during CMS repair. "
								+ "siteInfoProcessId=" + claimedProcess.getSiteInfoProcessId());
					}
					resultStatus = releaseResult.get(0).getLastResultStatus() == null
							? ProcessStatus.SUCCESS : releaseResult.get(0).getLastResultStatus();
					released = true;
					return claimedProcess;
				}
				SiteCategory category = claimedProcess.getSiteCategory();
				List<SiteContents> contentsList = contentsParser.loadCategoryContentsList(category);
				if (contentsList == null || contentsList.isEmpty()) {
					log.warn("No contents found for category: {}", category.getCategoryUrl());
					throw new IllegalStateException("No contents found. siteInfoProcessId="
							+ claimedProcess.getSiteInfoProcessId());
				}

				List<SiteContents> loadedContents = new ArrayList<>();
				for (SiteContents contents : limitContents(contentsList)) {
					try {
						SiteContents fullContents = contentsParser.loadContents(contents);
						loadedContents.add(fullContents);
					} catch (NotFoundContentsException e) {
						RunFatalFailure.propagateIfPresent(e);
						log.warn("Content not found: {}", contents.getUrl(), e);
					}
				}

				if (loadedContents.isEmpty()) {
					log.warn("No content details loaded for category: {}", category.getCategoryUrl());
					throw new IllegalStateException("No content details loaded. siteInfoProcessId="
							+ claimedProcess.getSiteInfoProcessId());
				}

				siteContentsService.bulkInsertIfNotExists(loadedContents);
				crawlJobSummary.incrementCrawlSuccess();
				List<SiteInfoProcessPool> releaseResult = siteContentsService.saveAllProcessPools(List.of(claimedProcess));
				if (releaseResult.isEmpty()) {
					throw new IllegalStateException("Process pool lease was lost before release. siteInfoProcessId="
							+ claimedProcess.getSiteInfoProcessId());
				}
				resultStatus = releaseResult.get(0).getLastResultStatus() == null
						? ProcessStatus.SUCCESS : releaseResult.get(0).getLastResultStatus();
				released = true;
				return claimedProcess;
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				crawlJobSummary.incrementCrawlFailure();
				log.warn("Error processing siteInfoProcessPool: {}", claimedProcess.getSiteInfoProcessId(), e);
				return null;
			} finally {
				// saveAllProcessPools normally releases first; this fenced fallback covers every exception path.
				if (!released) {
					siteContentsService.finishOwnedProcess(claimedProcess, resultStatus);
				}
			}
		};
	}

	private List<SiteContents> limitContents(List<SiteContents> contentsList) {
		int limit = Math.max(1, crawlContentLimitCount);
		if (contentsList.size() <= limit) {
			return contentsList;
		}
		log.info("Limit crawl contents. total={}, limit={}", contentsList.size(), limit);
		return contentsList.stream().limit(limit).toList();
	}

	@Bean
	public ItemWriter<SiteInfoProcessPool> siteInfoProcessWriter() {
		return chunk -> {
			// Processing, CMS publication, and lease release are one guarded processor lifecycle.
		};
	}

	private Integer categoryId(SiteInfoProcessPool pool) {
		return pool == null || pool.getSiteCategory() == null ? null : pool.getSiteCategory().getSiteCategoryId();
	}

	private String currentProcessOwner() {
		if (processOwner != null && !"job-manual".equals(processOwner)) {
			return processOwner;
		}
		StepContext context = StepSynchronizationManager.getContext();
		if (context == null || context.getStepExecution() == null
				|| context.getStepExecution().getJobExecution() == null) {
			return "job-manual";
		}
		return "job-" + context.getStepExecution().getJobExecution().getId();
	}
}
