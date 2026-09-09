package com.happinesea.webcrawler.config;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.StepSynchronizationManager;

import com.happinesea.webcrawler.ContentsParser;
import com.happinesea.webcrawler.config.CategoryAssignmentPlan.CategoryAssignment;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.service.CrawlJobSummary;
import com.happinesea.webcrawler.service.SiteContentsService;

@ExtendWith(MockitoExtension.class)
class CrawlerComponentsOwnershipTest {

    @Mock
    private SiteContentsService siteContentsService;

    @Mock
    private ContentsParser contentsParser;

    private CrawlerComponents components;
    private SiteInfoProcessPool process;
	private CrawlJobSummary crawlJobSummary;

    @BeforeEach
    void setUp() {
        components = new CrawlerComponents();
        components.setSiteContentsService(siteContentsService);
        components.setContentsParser(contentsParser);
		crawlJobSummary = new CrawlJobSummary();
		components.setCrawlJobSummary(crawlJobSummary);
        components.setCrawlContentLimitCount(10);

        SiteCategory category = new SiteCategory();
        category.setCategoryUrl("https://example.com/category");
        process = new SiteInfoProcessPool();
        process.setSiteInfoProcessId(7);
        process.setSiteCategory(category);
    }

    @AfterEach
    void clearStepContext() {
        StepSynchronizationManager.close();
		Thread.interrupted();
    }

    @Test
    void processorUsesJobExecutionIdAsClaimOwner() throws Exception {
        registerJobExecution(42L);
		when(siteContentsService.claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("/job-42/")))
				.thenReturn(Optional.empty());

        assertNull(components.siteInfoProcessor().process(process));

		verify(siteContentsService).claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("/job-42/"));
        verifyNoInteractions(contentsParser);
    }

    @Test
    void explicitOwnerOverridesStepContext() throws Exception {
        components.setProcessOwner("job-recovery-test");
        registerJobExecution(42L);
		when(siteContentsService.claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("/job-recovery-test/"))).thenReturn(Optional.empty());

        assertNull(components.siteInfoProcessor().process(process));

		verify(siteContentsService).claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("/job-recovery-test/"));
        verifyNoInteractions(contentsParser);
    }

    @Test
    void processorSkipsParsingWhenAnotherExecutionAlreadyClaimedPool() throws Exception {
        registerJobExecution(99L);
		when(siteContentsService.claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7),
				org.mockito.ArgumentMatchers.contains("/job-99/")))
				.thenReturn(Optional.empty());

        assertNull(components.siteInfoProcessor().process(process));

        verifyNoInteractions(contentsParser);
    }

	@Test
	void processorRecordsFailureAndRunsFencedCleanupWhenNormalCompletionLosesLease() throws Exception {
		SiteContents claimedContents = contents(process);
		when(siteContentsService.claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7), anyString()))
				.thenReturn(Optional.of(process));
		when(contentsParser.loadCategoryContentsList(process.getSiteCategory())).thenReturn(List.of(claimedContents));
		when(contentsParser.loadContents(claimedContents)).thenReturn(claimedContents);
		when(siteContentsService.saveAllProcessPools(List.of(process))).thenReturn(List.of());

		assertNull(components.siteInfoProcessor().process(process));

		assertEquals(1, crawlJobSummary.snapshot().crawlFailureCount());
		verify(siteContentsService).finishOwnedProcess(process,
				com.happinesea.webcrawler.Const.ProcessStatus.FAIL);
	}

	@Test
	void processorRecordsFailureAndRunsFencedCleanupWhenRepairCompletionLosesLease() throws Exception {
		when(siteContentsService.claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(7), anyString()))
				.thenReturn(Optional.of(process));
		when(siteContentsService.isCmsRepairConfigured()).thenReturn(true);
		when(siteContentsService.repairConfiguredCmsContent(List.of(process))).thenReturn(List.of());

		assertNull(components.siteInfoProcessor().process(process));

		assertEquals(1, crawlJobSummary.snapshot().crawlFailureCount());
		verify(siteContentsService).finishOwnedProcess(process,
				com.happinesea.webcrawler.Const.ProcessStatus.FAIL);
	}

	@Test
	void workerProcessesOnlyItsAssignedCategoriesSequentially() throws Exception {
		SiteInfoProcessPool first = process(1, 11);
		SiteInfoProcessPool second = process(2, 12);
		SiteInfoProcessPool unassigned = process(3, 13);
		SiteContents firstContents = contents(first);
		SiteContents secondContents = contents(second);
		when(siteContentsService.claimSiteInfoProcess(any(), anyString()))
				.thenReturn(Optional.of(first), Optional.of(second));
		when(contentsParser.loadCategoryContentsList(first.getSiteCategory())).thenReturn(List.of(firstContents));
		when(contentsParser.loadCategoryContentsList(second.getSiteCategory())).thenReturn(List.of(secondContents));
		when(contentsParser.loadContents(firstContents)).thenReturn(firstContents);
		when(contentsParser.loadContents(secondContents)).thenReturn(secondContents);
		when(siteContentsService.saveAllProcessPools(any())).thenReturn(List.of(first), List.of(second));

		components.categoryAssignmentProcessor().process(new CategoryAssignment(2, List.of(first, second)));

		InOrder ordered = inOrder(contentsParser);
		ordered.verify(contentsParser).loadCategoryContentsList(first.getSiteCategory());
		ordered.verify(contentsParser).loadContents(firstContents);
		ordered.verify(contentsParser).loadCategoryContentsList(second.getSiteCategory());
		ordered.verify(contentsParser).loadContents(secondContents);
		verify(siteContentsService, never()).claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(
				unassigned.getSiteInfoProcessId()), anyString());
	}

	@Test
	void recoverableCategoryFailureReleasesFailAndContinuesWithNextAssignmentCategory() throws Exception {
		SiteInfoProcessPool first = process(1, 11);
		SiteInfoProcessPool second = process(2, 12);
		SiteContents secondContents = contents(second);
		when(siteContentsService.claimSiteInfoProcess(any(), anyString()))
				.thenReturn(Optional.of(first), Optional.of(second));
		when(contentsParser.loadCategoryContentsList(first.getSiteCategory()))
				.thenThrow(new IllegalStateException("source unavailable"));
		when(contentsParser.loadCategoryContentsList(second.getSiteCategory())).thenReturn(List.of(secondContents));
		when(contentsParser.loadContents(secondContents)).thenReturn(secondContents);
		when(siteContentsService.saveAllProcessPools(any())).thenReturn(List.of(second));

		components.categoryAssignmentProcessor().process(new CategoryAssignment(1, List.of(first, second)));

		verify(siteContentsService).finishOwnedProcess(first, com.happinesea.webcrawler.Const.ProcessStatus.FAIL);
		verify(contentsParser).loadCategoryContentsList(second.getSiteCategory());
	}

	@Test
	void databaseFailureIsRunFatalAndStopsRemainingAssignmentCategories() throws Exception {
		SiteInfoProcessPool first = process(1, 11);
		SiteInfoProcessPool second = process(2, 12);
		SiteContents firstContents = contents(first);
		when(siteContentsService.claimSiteInfoProcess(any(), anyString())).thenReturn(Optional.of(first));
		when(contentsParser.loadCategoryContentsList(first.getSiteCategory())).thenReturn(List.of(firstContents));
		when(contentsParser.loadContents(firstContents)).thenReturn(firstContents);
		org.mockito.Mockito.doThrow(new DataAccessResourceFailureException("database unavailable"))
				.when(siteContentsService).bulkInsertIfNotExists(List.of(firstContents));

		assertThrows(DataAccessResourceFailureException.class, () -> components.categoryAssignmentProcessor()
				.process(new CategoryAssignment(1, List.of(first, second))));

		verify(siteContentsService, never()).claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(
				second.getSiteInfoProcessId()), anyString());
	}

	@Test
	void wrappedInterruptInNotFoundContentsIsRunFatalAndStopsRemainingCategories() throws Exception {
		SiteInfoProcessPool first = process(1, 11);
		SiteInfoProcessPool second = process(2, 12);
		SiteContents firstContents = contents(first);
		when(siteContentsService.claimSiteInfoProcess(any(), anyString())).thenReturn(Optional.of(first));
		when(contentsParser.loadCategoryContentsList(first.getSiteCategory())).thenReturn(List.of(firstContents));
		when(contentsParser.loadContents(firstContents)).thenThrow(
				new com.happinesea.webcrawler.NotFoundContentsException("interrupted detail",
						new InterruptedException("stop")));

		assertThrows(RuntimeException.class, () -> components.categoryAssignmentProcessor()
				.process(new CategoryAssignment(1, List.of(first, second))));

		assertTrue(Thread.currentThread().isInterrupted());
		verify(siteContentsService, never()).claimSiteInfoProcess(org.mockito.ArgumentMatchers.eq(
				second.getSiteInfoProcessId()), anyString());
	}

	private SiteInfoProcessPool process(int poolId, int categoryId) {
		SiteCategory category = new SiteCategory();
		category.setSiteCategoryId(categoryId);
		category.setCategoryUrl("https://example.com/category/" + categoryId);
		SiteInfoProcessPool pool = new SiteInfoProcessPool();
		pool.setSiteInfoProcessId(poolId);
		pool.setSiteCategory(category);
		return pool;
	}

	private SiteContents contents(SiteInfoProcessPool pool) {
		SiteContents contents = new SiteContents();
		contents.setSiteCategory(pool.getSiteCategory());
		contents.setUrl(pool.getSiteCategory().getCategoryUrl() + "/article");
		return contents;
	}

    private void registerJobExecution(Long jobExecutionId) {
        JobExecution jobExecution = new JobExecution(jobExecutionId);
        StepExecution stepExecution = new StepExecution("crawlStep", jobExecution);
        StepSynchronizationManager.register(stepExecution);
    }
}
