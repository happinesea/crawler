package com.happinesea.webcrawler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import java.util.Collections;
import java.util.List;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.support.ListItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.config.CategoryAssignmentPlan.CategoryAssignment;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfo;
import com.happinesea.webcrawler.repository.SiteCategoryRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;
import com.happinesea.webcrawler.service.CrawlJobSummary;
import com.happinesea.webcrawler.service.SiteContentsService;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase
@ExtendWith(MockitoExtension.class)
class BatchConfigTest {
    @Mock
    private SiteInfoProcessRepository siteInfoProcessRepository;

    @Mock
    private SiteCategoryRepository siteCategoryRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private CrawlerComponents crawlerComponents;
    @Mock
    private SiteContentsService siteContentsService;

    private CrawlJobSummary crawlJobSummary;

    @Mock
    private ItemProcessor<SiteInfoProcessPool, SiteInfoProcessPool> processor;
    @Mock
    private ItemReader<SiteInfoProcessPool> reader;

    @Mock
    private ItemWriter<SiteInfoProcessPool> writer;

    @Mock
    private ItemReader<CategoryAssignment> assignmentReader;

    @Mock
    private ItemProcessor<CategoryAssignment, CategoryAssignment> assignmentProcessor;

    @Mock
    private ItemWriter<CategoryAssignment> assignmentWriter;

    private BatchConfig batchConfig;

    @BeforeEach
    void setUp() {
        batchConfig = new BatchConfig();
        crawlJobSummary = new CrawlJobSummary();
        ReflectionTestUtils.setField(batchConfig, "siteInfoProcessRepository", siteInfoProcessRepository);
        ReflectionTestUtils.setField(batchConfig, "siteCategoryRepository", siteCategoryRepository);
        ReflectionTestUtils.setField(batchConfig, "siteContentsService", siteContentsService);
        ReflectionTestUtils.setField(batchConfig, "crawlJobSummary", crawlJobSummary);
        ReflectionTestUtils.setField(batchConfig, "crawlProcessLimitCount", 1);
        ReflectionTestUtils.setField(batchConfig, "targetSiteInfoIds", "");
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "");
        ReflectionTestUtils.setField(batchConfig, "repairCmsContentId", null);
        lenient().when(siteCategoryRepository.findActiveCategories()).thenReturn(Collections.emptyList());
    }

    @Test
    void repairModeLimitsTargetProcessesToOneLease() {
        ReflectionTestUtils.setField(batchConfig, "crawlProcessLimitCount", 10);
        ReflectionTestUtils.setField(batchConfig, "repairCmsContentId", 2643L);
        SiteInfoProcessPool first = createPool(1, 1, 5);
        SiteInfoProcessPool second = createPool(2, 1, 3);

        List<SiteInfoProcessPool> result = ReflectionTestUtils.invokeMethod(
                batchConfig, "freezeTargetProcesses", List.of(first, second));

        assertEquals(1, result.size());
        assertEquals(2, result.get(0).getSiteInfoProcessId());
    }

    @Test
    void testCrawlStep_returnsEmptyStepIfNoData() {
        when(siteContentsService.findAliveProcess()).thenReturn(Collections.emptyList());

        Step step = batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);

        assertNotNull(step);
        assertEquals("emptyStep", step.getName());
    }

    @Test
    void testCrawlStep_returnsNormalStepIfDataExists() {
        SiteInfoProcessPool p1 = new SiteInfoProcessPool();
        p1.setSiteInfoProcessId(1);
        p1.setSiteCategory(createCategory(1, 1, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics"));
        SiteInfoProcessPool p2 = new SiteInfoProcessPool();
        p2.setSiteInfoProcessId(2);
        p2.setSiteCategory(createCategory(1, 2, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics/domestic"));

        List<SiteInfoProcessPool> list = List.of(p1, p2);

        when(siteContentsService.findAliveProcess()).thenReturn(list);
        when(crawlerComponents.categoryAssignmentReader(anyList())).thenReturn(assignmentReader);
        when(crawlerComponents.categoryAssignmentProcessor()).thenReturn(assignmentProcessor);
        when(crawlerComponents.categoryAssignmentWriter()).thenReturn(assignmentWriter);

        Step step = batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);

        assertNotNull(step);
        assertEquals("crawlStep", step.getName());
    }

    @Test
    void testCrawlStepBuildsParallelStepAndShutsDownExecutorListener() throws Exception {
        ReflectionTestUtils.setField(batchConfig, "crawlProcessLimitCount", 10);

        SiteInfoProcessPool p1 = new SiteInfoProcessPool();
        p1.setSiteInfoProcessId(1);
        p1.setSiteCategory(createCategory(1, 1, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics"));
        SiteInfoProcessPool p2 = new SiteInfoProcessPool();
        p2.setSiteInfoProcessId(2);
        p2.setSiteCategory(createCategory(1, 2, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics/domestic"));
        List<SiteInfoProcessPool> list = List.of(p1, p2);

        when(siteContentsService.findAliveProcess()).thenReturn(list);
        when(crawlerComponents.categoryAssignmentReader(anyList())).thenReturn(new ListItemReader<>(List.of(
                new CategoryAssignment(1, List.of(p1)), new CategoryAssignment(2, List.of(p2)))));
        when(crawlerComponents.categoryAssignmentProcessor()).thenReturn(item -> item);
        when(crawlerComponents.categoryAssignmentWriter()).thenReturn(chunk -> {
        });

        Step step = batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);
        StepExecutionListener listener = ReflectionTestUtils.invokeMethod(step, "getCompositeListener");
        StepExecution stepExecution = new StepExecution(step.getName(), new JobExecution(1L));

        listener.beforeStep(stepExecution);
        listener.afterStep(stepExecution);

        assertEquals("crawlStep", step.getName());
    }

    @Test
    void testCrawlJob_buildsJob() {
        Step step = new StepBuilder("mockStep", jobRepository)
                .tasklet((contribution, chunkContext) -> RepeatStatus.FINISHED, transactionManager)
                .build();

        Job job = batchConfig.crawlJob(jobRepository, step);

        assertNotNull(job);
        assertEquals("crawlJob", job.getName());
    }

    @Test
    void testCrawlStepDoesNotCapEligibleCategorySnapshotAtLogicalWorkerCount() {
        SiteInfoProcessPool p1 = new SiteInfoProcessPool();
        p1.setSiteInfoProcessId(1);
        p1.setSiteCategory(createCategory(1, 1, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics"));
        SiteInfoProcessPool p2 = new SiteInfoProcessPool();
        p2.setSiteInfoProcessId(2);
        p2.setSiteCategory(createCategory(1, 2, "https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics/domestic"));

        when(siteContentsService.findAliveProcess()).thenReturn(List.of(p1, p2));
        when(crawlerComponents.categoryAssignmentReader(anyList())).thenReturn(assignmentReader);
        when(crawlerComponents.categoryAssignmentProcessor()).thenReturn(assignmentProcessor);
        when(crawlerComponents.categoryAssignmentWriter()).thenReturn(assignmentWriter);

        batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);

        ArgumentCaptor<List<CategoryAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(crawlerComponents).categoryAssignmentReader(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(List.of(1, 2), captor.getValue().get(0).categoryIds());
    }

    @Test
    void crawlStepSnapshotsAllEligibleCategoriesOnceAndSubmitsNonemptyAssignments() {
        ReflectionTestUtils.setField(batchConfig, "crawlProcessLimitCount", 5);
        List<SiteInfoProcessPool> candidates = List.of(
                createPool(30, 1, 3),
                createPool(10, 1, 1),
                createPool(20, 1, 2));
        when(siteContentsService.findAliveProcess()).thenReturn(candidates);
        when(crawlerComponents.categoryAssignmentReader(anyList())).thenReturn(assignmentReader);
        when(crawlerComponents.categoryAssignmentProcessor()).thenReturn(assignmentProcessor);
        when(crawlerComponents.categoryAssignmentWriter()).thenReturn(assignmentWriter);

        batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CategoryAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(crawlerComponents).categoryAssignmentReader(captor.capture());
        verify(siteContentsService, times(1)).findAliveProcess();
        assertEquals(1, captor.getValue().size());
        assertEquals(5, captor.getValue().get(0).workerNumber());
        assertEquals(List.of(1, 2, 3), captor.getValue().get(0).categoryIds());
    }

    @Test
    void testCrawlStepFiltersTargetSiteInfoIdsBeforeApplyingLimit() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteInfoIds", "2");

        SiteInfoProcessPool yahoo = new SiteInfoProcessPool();
        yahoo.setSiteInfoProcessId(1);
        yahoo.setSiteCategory(createCategory(1, 1, "https://news.yahoo.co.jp",
                "https://news.yahoo.co.jp/topics"));
        SiteInfoProcessPool happinesea = new SiteInfoProcessPool();
        happinesea.setSiteInfoProcessId(2);
        happinesea.setSiteCategory(createCategory(2, 2, "https://happinesea.com",
                "https://happinesea.com/category/news"));

        when(siteContentsService.findAliveProcess()).thenReturn(List.of(yahoo, happinesea));
        when(crawlerComponents.categoryAssignmentReader(anyList())).thenReturn(assignmentReader);
        when(crawlerComponents.categoryAssignmentProcessor()).thenReturn(assignmentProcessor);
        when(crawlerComponents.categoryAssignmentWriter()).thenReturn(assignmentWriter);

        batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents);

        ArgumentCaptor<List<CategoryAssignment>> captor = ArgumentCaptor.forClass(List.class);
        verify(crawlerComponents).categoryAssignmentReader(captor.capture());
        assertEquals(1, captor.getValue().size());
        assertEquals(List.of(2), captor.getValue().get(0).categoryIds());
    }

    @Test
    void testTargetFiltersEmptyPreserveCandidateList() {
        List<SiteInfoProcessPool> candidates = List.of(
                createPool(1, 1, 3),
                createPool(2, 1, 8));

        assertEquals(candidates, batchConfig.filterTargetProcesses(candidates));
    }

    @Test
    void testTargetCategoryFilterSelectsOnlyConfiguredCategory() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "3");
        List<SiteInfoProcessPool> candidates = List.of(
                createPool(1, 1, 3),
                createPool(2, 1, 8));

        List<SiteInfoProcessPool> result = batchConfig.filterTargetProcesses(candidates);

        assertEquals(List.of(1), result.stream().map(SiteInfoProcessPool::getSiteInfoProcessId).toList());
    }

    @Test
    void testTargetCategoryFilterSupportsMultipleCategories() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "3,8");
        List<SiteInfoProcessPool> candidates = List.of(
                createPool(1, 1, 3),
                createPool(2, 1, 8),
                createPool(3, 1, 9));

        List<SiteInfoProcessPool> result = batchConfig.filterTargetProcesses(candidates);

        assertEquals(List.of(1, 2), result.stream().map(SiteInfoProcessPool::getSiteInfoProcessId).toList());
    }

    @Test
    void testSiteAndCategoryFiltersUseAndSemantics() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteInfoIds", "1");
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "3");
        List<SiteInfoProcessPool> candidates = List.of(
                createPool(1, 1, 3),
                createPool(2, 2, 3),
                createPool(3, 1, 8));

        List<SiteInfoProcessPool> result = batchConfig.filterTargetProcesses(candidates);

        assertEquals(List.of(1), result.stream().map(SiteInfoProcessPool::getSiteInfoProcessId).toList());
    }

    @Test
    void testSiteAndCategoryFiltersRejectCategoryOwnedByAnotherSite() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteInfoIds", "2");
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "3");

        List<SiteInfoProcessPool> result = batchConfig.filterTargetProcesses(
                List.of(createPool(1, 1, 3)));

        assertTrue(result.isEmpty());
    }

    @Test
    void testUnknownTargetCategoryReturnsNoCandidates() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "999");

        List<SiteInfoProcessPool> result = batchConfig.filterTargetProcesses(
                List.of(createPool(1, 1, 3)));

        assertTrue(result.isEmpty());
    }

    @Test
    void testInvalidTargetCategoryFailsFast() {
        ReflectionTestUtils.setField(batchConfig, "targetSiteCategoryIds", "3,invalid");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> batchConfig.crawlStep(jobRepository, transactionManager, crawlerComponents));

        assertTrue(error.getMessage().contains("web-crawler.target-site-category-ids"));
        verifyNoInteractions(siteCategoryRepository, siteContentsService);
    }

    @Test
    void testEnsureProcessPoolsForActiveCategoriesCreatesMissingPools() {
        SiteCategory categoryWithPool = createCategory("https://news.yahoo.co.jp", "https://news.yahoo.co.jp/topics");
        categoryWithPool.setSiteCategoryId(1);
        SiteCategory categoryWithoutPool = createCategory("https://news.yahoo.co.jp",
                "https://news.yahoo.co.jp/topics/domestic");
        categoryWithoutPool.setSiteCategoryId(2);

        SiteInfoProcessPool existingPool = new SiteInfoProcessPool();
        existingPool.setSiteInfoProcessId(10);
        existingPool.setSiteCategory(categoryWithPool);

        when(siteCategoryRepository.findActiveCategories()).thenReturn(List.of(categoryWithPool, categoryWithoutPool));
        when(siteInfoProcessRepository.findAll()).thenReturn(List.of(existingPool));

        batchConfig.ensureProcessPoolsForActiveCategories();

        ArgumentCaptor<SiteInfoProcessPool> captor = ArgumentCaptor.forClass(SiteInfoProcessPool.class);
        verify(siteInfoProcessRepository).save(captor.capture());
        assertNull(captor.getValue().getSiteInfoProcessId());
        assertEquals(ProcessStatus.NONE, captor.getValue().getProcessStatus());
        assertEquals(2, captor.getValue().getSiteCategory().getSiteCategoryId());
    }

    @Test
    void testCategoryRecoveryChunkSizeIsOne() {
        assertEquals(1, BatchConfig.CATEGORY_RECOVERY_CHUNK_SIZE);
    }

    @Test
    void testStepTransactionManagerSuspendsSynchronizationOnlyDuringItemProcessing() throws Exception {
        PlatformTransactionManager stepTransactionManager = BatchConfig.createStepTransactionManager();
        SiteInfoProcessPool item = new SiteInfoProcessPool();
        when(processor.process(item)).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            return item;
        });
        ItemProcessor<SiteInfoProcessPool, SiteInfoProcessPool> processorWithoutStepTransaction =
                BatchConfig.withoutStepTransaction(processor, stepTransactionManager);

        new TransactionTemplate(stepTransactionManager).executeWithoutResult(status -> {
            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            });

            assertEquals(item, assertDoesNotThrow(() -> processorWithoutStepTransaction.process(item)));

            assertTrue(TransactionSynchronizationManager.isSynchronizationActive());
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
        });
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
    }

    private SiteCategory createCategory(String siteUrl, String categoryUrl) {
        return createCategory(null, siteUrl, categoryUrl);
    }

    private SiteCategory createCategory(Integer siteInfoId, String siteUrl, String categoryUrl) {
        return createCategory(siteInfoId, null, siteUrl, categoryUrl);
    }

    private SiteCategory createCategory(Integer siteInfoId, Integer siteCategoryId, String siteUrl,
            String categoryUrl) {
        SiteInfo siteInfo = new SiteInfo();
        siteInfo.setSiteInfoId(siteInfoId);
        siteInfo.setSiteUrl(siteUrl);
        SiteCategory category = new SiteCategory();
        category.setSiteCategoryId(siteCategoryId);
        category.setSiteInfo(siteInfo);
        category.setCategoryUrl(categoryUrl);
        return category;
    }

    private SiteInfoProcessPool createPool(int poolId, int siteInfoId, int siteCategoryId) {
        SiteInfoProcessPool pool = new SiteInfoProcessPool();
        pool.setSiteInfoProcessId(poolId);
        pool.setSiteCategory(createCategory(siteInfoId, siteCategoryId,
                "https://site-" + siteInfoId + ".example",
                "https://site-" + siteInfoId + ".example/category/" + siteCategoryId));
        return pool;
    }
}

