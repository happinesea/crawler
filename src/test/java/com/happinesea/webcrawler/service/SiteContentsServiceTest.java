package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.TransactionSystemException;

import com.happinesea.webcrawler.ContentsParser;
import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfo;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteCategoryRepository;
import com.happinesea.webcrawler.repository.SiteContentsRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;
import com.happinesea.webcrawler.repository.SiteInfoRepository;

import jakarta.transaction.Transactional;

@SpringBootTest
@ActiveProfiles("test")
class SiteContentsServiceTest {

	@Autowired
	private SiteContentsService siteContentsService;

	@Autowired
	private SiteContentsRepository siteContentsRepository;

	@Autowired
	private SiteCategoryRepository siteCategoryRepository;

	@Autowired
	private SiteInfoRepository siteInfoRepository;

	@Autowired
	private CrawlJobSummary crawlJobSummary;

	@Autowired
	private SiteInfoProcessRepository siteInfoProcessRepository;

	@MockitoBean
	private WordPressPostService wordPressPostService;

	@MockitoBean
	private AiAnalysisService aiAnalysisService;

	@MockitoBean
	private ContentsParser contentsParser;

	List<SiteContents> contentsList;

	private SiteCategory category1;

	private SiteCategory category2;

	private SiteInfoProcessPool pool;

	@BeforeEach
	void setUp() throws Exception {
		crawlJobSummary.reset();
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", true);
		ReflectionTestUtils.setField(siteContentsService, "postFeaturedImageOnly", false);
		ReflectionTestUtils.setField(siteContentsService, "syncExistingCmsContent", true);
		ReflectionTestUtils.setField(siteContentsService, "repairCmsContentId", null);
		when(aiAnalysisService.prepareForPost(any(SiteContents.class))).thenAnswer(invocation -> invocation.getArgument(0));
		siteInfoProcessRepository.deleteAll();
		siteContentsRepository.deleteAll();
		siteCategoryRepository.deleteAll();
		siteInfoRepository.deleteAll();

		contentsList = new ArrayList<SiteContents>();

		SiteInfo site = new SiteInfo();
		category1 = new SiteCategory();
		category1.setCategoryName("name1");
		category1.setSiteInfo(siteInfoRepository.save(site));
		category1 = siteCategoryRepository.save(category1);

		category2 = new SiteCategory();
		category2.setCategoryName("name2");
		category2.setSiteInfo(siteInfoRepository.save(site));
		category2 = siteCategoryRepository.save(category2);

		SiteContents s1 = new SiteContents();
		s1.setProcessStatus(ProcessStatus.NONE);
		s1.setUrl("http://url1");
		s1.setTitle("title1");
		s1.setContents("contents1");
		s1.setSiteCategory(category1);

		SiteContents s2 = new SiteContents();
		s2.setProcessStatus(ProcessStatus.PROCESSING);
		s2.setUrl("http://url2");
		s2.setTitle("title2");
		s2.setContents("contents2");
		s2.setSiteCategory(category1);

		SiteContents s3 = new SiteContents();
		s3.setProcessStatus(ProcessStatus.SUCCESS);
		s3.setUrl("http://url3");
		s3.setTitle("title3");
		s3.setContents("contents3");
		s3.setSiteCategory(category1);

		SiteContents s4 = new SiteContents();
		s4.setProcessStatus(ProcessStatus.PROCESSING);
		s4.setUrl("http://url4");
		s4.setTitle("title4");
		s4.setContents("contents4");
		s4.setSiteCategory(category1);

		contentsList.add(s1);
		contentsList.add(s2);
		contentsList.add(s3);
		contentsList.add(s4);

		pool = new SiteInfoProcessPool();
		pool.setProcessStatus(ProcessStatus.NONE);
		pool.setProcessTime(LocalDateTime.now());
		pool.setSiteCategory(category1);

		pool = siteInfoProcessRepository.save(pool);
	}

	@Test
	void repairConfiguredCmsContentReparsesBlankFeaturedImageAndUpdatesOnlyExistingPost() throws Exception {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "repairCmsContentId", 2643L);

		SiteContents existing = createPostTargetContents(category2, "https://source.example/article",
				"repair title", "<p>existing body</p>");
		existing.setProcessStatus(ProcessStatus.SUCCESS);
		existing.setCmsContentId(2643L);
		existing.setFeaturedImageUrl(null);
		existing = siteContentsRepository.save(existing);

		SiteInfoProcessPool repairPool = new SiteInfoProcessPool();
		repairPool.setSiteCategory(category2);
		repairPool.setProcessStatus(ProcessStatus.PROCESSING);
		repairPool.setProcessId("job-repair");
		repairPool.setLeaseAttempt(1L);
		repairPool.setProcessTime(LocalDateTime.now());
		repairPool = siteInfoProcessRepository.save(repairPool);

		when(contentsParser.loadContents(any(SiteContents.class))).thenAnswer(invocation -> {
			SiteContents refreshed = invocation.getArgument(0);
			refreshed.setFeaturedImageUrl("https://source.example/images/featured.jpg");
			return refreshed;
		});
		when(wordPressPostService.repairImagePolicyIfNeeded(any(SiteContents.class))).thenReturn(true);

		List<SiteInfoProcessPool> result = siteContentsService
				.repairConfiguredCmsContent(List.of(repairPool));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getLastResultStatus());
		SiteContents saved = siteContentsRepository.findById(existing.getSiteContentsId()).orElseThrow();
		assertEquals("https://source.example/images/featured.jpg", saved.getFeaturedImageUrl());
		assertEquals(2643L, saved.getCmsContentId());
		assertEquals(ProcessStatus.SUCCESS, saved.getProcessStatus());
		verify(contentsParser).loadContents(any(SiteContents.class));
		verify(wordPressPostService).repairImagePolicyIfNeeded(any(SiteContents.class));
		verify(wordPressPostService, never()).post(any(SiteContents.class));
		verify(wordPressPostService, never()).updateCategories(any(SiteContents.class));
	}

	@Test
	void repairConfiguredCmsContentReleasesPoolAsFailWhenSourceReparseFails() throws Exception {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "repairCmsContentId", 2643L);

		SiteContents existing = createPostTargetContents(category2, "https://source.example/missing",
				"repair failure", "<p>existing body</p>");
		existing.setProcessStatus(ProcessStatus.SUCCESS);
		existing.setCmsContentId(2643L);
		existing.setFeaturedImageUrl(null);
		siteContentsRepository.save(existing);

		SiteInfoProcessPool repairPool = new SiteInfoProcessPool();
		repairPool.setSiteCategory(category2);
		repairPool.setProcessStatus(ProcessStatus.PROCESSING);
		repairPool.setProcessId("job-repair-failure");
		repairPool.setLeaseAttempt(1L);
		repairPool.setProcessTime(LocalDateTime.now());
		repairPool = siteInfoProcessRepository.save(repairPool);

		when(contentsParser.loadContents(any(SiteContents.class)))
				.thenThrow(new RuntimeException("source unavailable"));

		List<SiteInfoProcessPool> result = siteContentsService
				.repairConfiguredCmsContent(List.of(repairPool));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.FAIL, result.get(0).getLastResultStatus());
		assertEquals(1, crawlJobSummary.snapshot().wordpressPostFailureCount());
		verify(wordPressPostService, never()).repairImagePolicyIfNeeded(any(SiteContents.class));
		verify(wordPressPostService, never()).post(any(SiteContents.class));
	}

	@Test
	void testBulkInsertIfNotExists() {
		siteContentsService.bulkInsertIfNotExists(contentsList);

		List<SiteContents> result = siteContentsRepository.findAll();

		assertEquals(4, result.size());
	}

	@Test
	void testBulkInsertIfNotExistsWhenInputIsNullOrEmptyReturnsEmptyList() {
		assertTrue(siteContentsService.bulkInsertIfNotExists(null).isEmpty());
		assertTrue(siteContentsService.bulkInsertIfNotExists(List.of()).isEmpty());
		assertTrue(siteContentsRepository.findAll().isEmpty());
	}

	@Test
	void testBulkInsertIfNotExistsSkipsNullBlankAndDuplicateUrls() {
		SiteContents nullContent = null;
		SiteContents blankUrl = new SiteContents();
		blankUrl.setUrl(" ");

		SiteContents first = new SiteContents();
		first.setUrl("http://unique-url");
		first.setTitle("first");
		first.setContents("body");
		first.setSiteCategory(category1);

		SiteContents duplicate = new SiteContents();
		duplicate.setUrl("http://unique-url");
		duplicate.setTitle("duplicate");
		duplicate.setContents("body");
		duplicate.setSiteCategory(category1);

		List<SiteContents> result = siteContentsService
				.bulkInsertIfNotExists(Arrays.asList(nullContent, blankUrl, first, duplicate));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.NONE, result.get(0).getProcessStatus());
		assertEquals("first", result.get(0).getTitle());
		assertEquals(1, siteContentsRepository.findAll().size());
	}

	@Test
	void testBulkInsertIfNotExistsWhenAllCandidatesAreInvalidReturnsEmptyList() {
		SiteContents blankUrl = new SiteContents();
		blankUrl.setUrl("");

		List<SiteContents> result = siteContentsService.bulkInsertIfNotExists(Arrays.asList(null, blankUrl));

		assertTrue(result.isEmpty());
		assertTrue(siteContentsRepository.findAll().isEmpty());
	}

	@Test
	void testBulkInsertIfNotExistsFiltersExistingUrls() {
		SiteContents existing = new SiteContents();
		existing.setProcessStatus(ProcessStatus.SUCCESS);
		existing.setUrl("http://already-exists");
		existing.setTitle("existing");
		existing.setContents("body");
		existing.setSiteCategory(category1);
		siteContentsRepository.save(existing);

		SiteContents candidate = new SiteContents();
		candidate.setProcessStatus(ProcessStatus.NONE);
		candidate.setUrl("http://already-exists");
		candidate.setTitle("candidate");
		candidate.setContents("body");
		candidate.setSiteCategory(category1);

		List<SiteContents> result = siteContentsService.bulkInsertIfNotExists(List.of(candidate));

		assertTrue(result.isEmpty());
		assertEquals(1, siteContentsRepository.findAll().size());
	}

	@Test
	void concurrentEquivalentUrlsInsertOnlyOneRow() throws Exception {
		SiteContents first = newContent("https://EXAMPLE.com:443/story/?utm_source=mail", "first");
		SiteContents second = newContent("https://example.com/story", "second");
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var one = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return siteContentsService.bulkInsertIfNotExists(List.of(first)).size();
			});
			var two = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return siteContentsService.bulkInsertIfNotExists(List.of(second)).size();
			});
			start.countDown();
			assertEquals(1, one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS));
			assertEquals(1, siteContentsRepository.findAll().size());
		} finally {
			executor.shutdownNow();
		}
	}

	private SiteContents newContent(String url, String title) {
		SiteContents contents = new SiteContents();
		contents.setUrl(url);
		contents.setTitle(title);
		contents.setContents("body");
		contents.setSiteCategory(category1);
		return contents;
	}

	@Test
	@Transactional
	void testFindAliveProcess() {
		pool.setProcessTime(LocalDateTime.now().minusMinutes(2));
		siteInfoProcessRepository.save(pool);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessTime(LocalDateTime.now().minusMinutes(1));
		pool2.setSiteCategory(category2);
		siteInfoProcessRepository.save(pool2);
		SiteInfoProcessPool pool3 = new SiteInfoProcessPool();
		pool3.setProcessStatus(ProcessStatus.SUCCESS);
		pool3.setProcessTime(LocalDateTime.now());
		pool3.setSiteCategory(category2);
		pool3 = siteInfoProcessRepository.save(pool3);

		List<SiteInfoProcessPool> result = siteContentsService.findAliveProcess();

		assertEquals(2, result.size());
		SiteInfoProcessPool v = result.get(0);
		assertEquals(pool.getSiteInfoProcessId(), v.getSiteInfoProcessId());
		assertEquals(ProcessStatus.NONE, v.getProcessStatus());
		assertEquals("name1", v.getSiteCategory().getCategoryName());
		assertEquals(pool3.getSiteInfoProcessId(), result.get(1).getSiteInfoProcessId());
		assertEquals(ProcessStatus.SUCCESS, result.get(1).getProcessStatus());
	}

	@Test
	void testProcessingBeforeTimeoutIsNotClaimCandidate() {
		SiteInfoProcessPool processing = new SiteInfoProcessPool();
		processing.setProcessStatus(ProcessStatus.PROCESSING);
		processing.setProcessId("job-old");
		processing.setProcessTime(LocalDateTime.now().minusMinutes(10));
		processing.setSiteCategory(category2);
		siteInfoProcessRepository.save(processing);

		List<SiteInfoProcessPool> result = siteContentsService.findAliveProcess();

		assertTrue(result.stream().noneMatch(p -> "job-old".equals(p.getProcessId())));
	}

	@Test
	void testProcessingAfterTimeoutIsClaimCandidateAndCanBeRecovered() {
		SiteInfoProcessPool processing = new SiteInfoProcessPool();
		processing.setProcessStatus(ProcessStatus.PROCESSING);
		processing.setProcessId("job-old");
		processing.setProcessTime(LocalDateTime.now().minusMinutes(61));
		processing.setSiteCategory(category2);
		processing = siteInfoProcessRepository.save(processing);
		Integer processingId = processing.getSiteInfoProcessId();

		List<SiteInfoProcessPool> result = siteContentsService.findAliveProcess();
		SiteInfoProcessPool recovered = siteContentsService
				.claimSiteInfoProcess(processingId, "job-new")
				.orElseThrow();

		assertTrue(result.stream().anyMatch(p -> processingId.equals(p.getSiteInfoProcessId())));
		assertEquals(ProcessStatus.PROCESSING, recovered.getProcessStatus());
		assertEquals("job-new", recovered.getProcessId());
	}

	@Test
	void testOldOwnerCannotOverwriteNewOwnerStatus() {
		SiteInfoProcessPool processing = new SiteInfoProcessPool();
		processing.setProcessStatus(ProcessStatus.PROCESSING);
		processing.setProcessId("job-new");
		processing.setProcessTime(LocalDateTime.now());
		processing.setSiteCategory(category2);
		processing = siteInfoProcessRepository.save(processing);

		SiteInfoProcessPool oldOwnerView = new SiteInfoProcessPool();
		oldOwnerView.setSiteInfoProcessId(processing.getSiteInfoProcessId());
		oldOwnerView.setProcessStatus(ProcessStatus.PROCESSING);
		oldOwnerView.setProcessId("job-old");
		oldOwnerView.setSiteCategory(category2);

		assertTrue(siteContentsService.finishOwnedProcess(oldOwnerView, ProcessStatus.SUCCESS).isEmpty());
		assertEquals(ProcessStatus.PROCESSING, siteInfoProcessRepository.findById(processing.getSiteInfoProcessId())
				.orElseThrow().getProcessStatus());
	}

	@Test
	void testSameCategoryCannotBeClaimedTwice() {
		SiteInfoProcessPool firstClaim = siteContentsService
				.claimSiteInfoProcess(pool.getSiteInfoProcessId(), "job-one")
				.orElseThrow();

		assertTrue(siteContentsService.claimSiteInfoProcess(pool.getSiteInfoProcessId(), "job-two").isEmpty());
		assertEquals("job-one", firstClaim.getProcessId());
		assertEquals("job-one", siteInfoProcessRepository.findById(pool.getSiteInfoProcessId())
				.orElseThrow().getProcessId());
	}

	@Test
	void testProcessPoolIdIsGeneratedByDatabase() {
		SiteInfoProcessPool generated = new SiteInfoProcessPool();
		generated.setProcessStatus(ProcessStatus.NONE);
		generated.setSiteCategory(category2);

		generated = siteInfoProcessRepository.save(generated);

		assertTrue(generated.getSiteInfoProcessId() != null && generated.getSiteInfoProcessId() > 0);
	}

	@Test
	void testChangSiteInfoProcess2Processing() {
		SiteInfoProcessPool result = siteContentsService.changSiteInfoProcess2Processing(pool);
		assertEquals(ProcessStatus.PROCESSING, result.getProcessStatus());
	}

	@Test
	void testChangSiteInfoProcess2Sucess() {
		SiteInfoProcessPool result = siteContentsService.changSiteInfoProcess2Sucess(pool);
		assertEquals(ProcessStatus.SUCCESS, result.getProcessStatus());

	}

	@Test
	void testChangSiteInfoProcess2Fail() {
		SiteInfoProcessPool result = siteContentsService.changSiteInfoProcess2Fail(pool);
		assertEquals(ProcessStatus.FAIL, result.getProcessStatus());

	}

	@Test
	void testSaveAllProcessPools() {
		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);
		
		List<SiteInfoProcessPool> p = new ArrayList<SiteInfoProcessPool>();
		p.add(pool);
		p.add(pool2);
		
		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(p);
		
		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		
	}

	@Test
	void testSaveAllProcessPoolsWhenCmsPostEnabledPostsContents() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		List<SiteInfoProcessPool> p = new ArrayList<SiteInfoProcessPool>();
		p.add(pool2);

		SiteContents contents = createPostTargetContents(category2, "http://post-success", "post title", "post body");
		contents = siteContentsRepository.save(contents);
		when(wordPressPostService.post(any(SiteContents.class))).thenReturn(123L);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(p);

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		SiteContents savedContents = siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.SUCCESS, savedContents.getProcessStatus());
		assertEquals(123L, savedContents.getCmsContentId());
		assertEquals("post body", savedContents.getDescription());
		verify(aiAnalysisService).prepareForPost(any(SiteContents.class));
		ArgumentCaptor<SiteContents> postTarget = ArgumentCaptor.forClass(SiteContents.class);
		verify(wordPressPostService).post(postTarget.capture());
		assertEquals("post body", postTarget.getValue().getDescription());
	}

	@Test
	void testSaveAllProcessPoolsBackfillsDescriptionBeforePosting() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents contents = createPostTargetContents(category2,
				"http://post-description-backfill", "post title", "<p>pending body</p>");
		contents.setDescription("");
		contents = siteContentsRepository.save(contents);
		when(wordPressPostService.post(any(SiteContents.class))).thenReturn(999L);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(pool2));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		SiteContents savedContents = siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow();
		assertEquals("pending body", savedContents.getDescription());
		ArgumentCaptor<SiteContents> postTarget = ArgumentCaptor.forClass(SiteContents.class);
		verify(wordPressPostService).post(postTarget.capture());
		assertEquals("pending body", postTarget.getValue().getDescription());
	}

	@Test
	void testSaveAllProcessPoolsSyncsCategoriesForExistingCmsContents() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents existingCmsContent = createPostTargetContents(category2,
				"http://already-posted", "posted title", "<p>posted body</p>");
		existingCmsContent.setProcessStatus(ProcessStatus.SUCCESS);
		existingCmsContent.setCmsContentId(777L);
		existingCmsContent.setDescription(null);
		existingCmsContent = siteContentsRepository.save(existingCmsContent);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(pool2));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		verify(wordPressPostService).updateCategories(any(SiteContents.class));
		SiteContents savedContent = siteContentsRepository.findById(existingCmsContent.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.SUCCESS, savedContent.getProcessStatus());
		assertEquals("posted body", savedContent.getDescription());
	}

	@Test
	void testSaveAllProcessPoolsCanSkipExistingCmsMaintenance() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "syncExistingCmsContent", false);
		ReflectionTestUtils.setField(siteContentsService, "postContentsLimitCount", 1);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents existing = createPostTargetContents(category2,
				"http://already-posted-no-maintenance", "posted title", "<p>posted body</p>");
		existing.setProcessStatus(ProcessStatus.SUCCESS);
		existing.setCmsContentId(777L);
		existing.setDescription(null);
		existing = siteContentsRepository.save(existing);
		SiteContents first = siteContentsRepository.save(createPostTargetContents(category2,
				"http://first-bounded-post", "first", "<p>first body</p>"));
		SiteContents beyondLimit = siteContentsRepository.save(createPostTargetContents(category2,
				"http://second-bounded-post", "second", "<p>second body</p>"));
		when(wordPressPostService.post(any(SiteContents.class))).thenReturn(778L);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(pool2));

		verify(wordPressPostService, never()).updateCategories(any(SiteContents.class));
		verify(wordPressPostService, times(1)).post(any(SiteContents.class));
		assertEquals(null, siteContentsRepository.findById(existing.getSiteContentsId()).orElseThrow().getDescription());
		SiteContents posted = siteContentsRepository.findById(first.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.SUCCESS, posted.getProcessStatus());
		assertEquals(778L, posted.getCmsContentId());
		SiteContents untouched = siteContentsRepository.findById(beyondLimit.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, untouched.getProcessStatus());
		assertEquals(null, untouched.getDescription());
		assertEquals(1, result.size());
		SiteInfoProcessPool released = siteInfoProcessRepository.findById(pool2.getSiteInfoProcessId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, released.getProcessStatus());
		assertEquals(ProcessStatus.SUCCESS, released.getLastResultStatus());
	}

	@Test
	void testSaveAllProcessPoolsCanLimitPostingToContentsWithFeaturedImage() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "postFeaturedImageOnly", true);
		ReflectionTestUtils.setField(siteContentsService, "postContentsLimitCount", 1);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents withoutImage = siteContentsRepository.save(createPostTargetContents(category2,
				"http://post-without-image", "no image", "body"));
		SiteContents withImage = createPostTargetContents(category2,
				"http://post-with-image", "with image", "body");
		withImage.setFeaturedImageUrl("https://example.com/article.jpg");
		withImage = siteContentsRepository.save(withImage);
		when(wordPressPostService.post(any(SiteContents.class))).thenReturn(124L);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(pool2));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.NONE, siteContentsRepository.findById(withoutImage.getSiteContentsId())
				.orElseThrow().getProcessStatus());
		SiteContents posted = siteContentsRepository.findById(withImage.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.SUCCESS, posted.getProcessStatus());
		assertEquals(124L, posted.getCmsContentId());
		ArgumentCaptor<SiteContents> target = ArgumentCaptor.forClass(SiteContents.class);
		verify(wordPressPostService).post(target.capture());
		assertEquals(withImage.getSiteContentsId(), target.getValue().getSiteContentsId());
		assertEquals("https://example.com/article.jpg", target.getValue().getFeaturedImageUrl());
	}

	@Test
	void testSaveAllProcessPoolsBackfillsBlankDescriptionsAcrossCmsLinkedContents() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", true);

		SiteContents existingCmsContent = createPostTargetContents(category2,
				"http://blank-description", "posted title", "<p>global posted body</p>");
		existingCmsContent.setProcessStatus(ProcessStatus.SUCCESS);
		existingCmsContent.setCmsContentId(888L);
		existingCmsContent.setDescription("");
		existingCmsContent = siteContentsRepository.save(existingCmsContent);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category1);
		pool2 = siteInfoProcessRepository.save(pool2);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(pool2));

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		assertEquals("global posted body", siteContentsRepository.findById(existingCmsContent.getSiteContentsId())
				.orElseThrow().getDescription());
	}

	@Test
	void testSaveAllProcessPoolsPostsAiAnalyzedContent() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents contents = createPostTargetContents(category2, "http://post-ai", "original title",
				"original body");
		contents = siteContentsRepository.save(contents);

		SiteContents analyzed = createPostTargetContents(category2, "http://post-ai", "AI title", "AI body");
		analyzed.setSiteContentsId(contents.getSiteContentsId());
		when(aiAnalysisService.prepareForPost(any(SiteContents.class))).thenReturn(analyzed);

		List<SiteInfoProcessPool> p = new ArrayList<SiteInfoProcessPool>();
		p.add(pool2);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(p);

		ArgumentCaptor<SiteContents> postTarget = ArgumentCaptor.forClass(SiteContents.class);
		verify(wordPressPostService).post(postTarget.capture());
		assertEquals(1, result.size());
		assertEquals(ProcessStatus.SUCCESS, result.get(0).getProcessStatus());
		assertEquals("AI title", postTarget.getValue().getTitle());
		assertEquals("AI body", postTarget.getValue().getContents());
		SiteContents savedContents = siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow();
		assertEquals("AI title", savedContents.getTitle());
		assertEquals("AI body", savedContents.getContents());
		assertEquals("original body", savedContents.getDescription());
	}

	@Test
	void testSaveAllProcessPoolsWhenWordPressPostFailsMarksFailAndContinues() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		when(wordPressPostService.post(any(SiteContents.class))).thenThrow(new RuntimeException("post failed"));

		SiteInfoProcessPool pool2 = new SiteInfoProcessPool();
		pool2.setProcessStatus(ProcessStatus.PROCESSING);
		pool2.setProcessId("job-manual");
		pool2.setProcessTime(LocalDateTime.now());
		pool2.setSiteCategory(category2);
		pool2 = siteInfoProcessRepository.save(pool2);

		SiteContents contents = createPostTargetContents(category2, "http://post-fail", "post title", "post body");
		contents = siteContentsRepository.save(contents);

		List<SiteInfoProcessPool> p = new ArrayList<SiteInfoProcessPool>();
		p.add(pool2);

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(p);

		assertEquals(1, result.size());
		assertEquals(ProcessStatus.FAIL, result.get(0).getProcessStatus());
		assertEquals(ProcessStatus.FAIL, siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow()
				.getProcessStatus());
		SiteInfoProcessPool releasedPool = siteInfoProcessRepository.findById(pool2.getSiteInfoProcessId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, releasedPool.getProcessStatus());
		assertEquals(ProcessStatus.FAIL, releasedPool.getLastResultStatus());
	}

	@Test
	void testSaveAllProcessPoolsPersistsCreatedPostIdWhenMetadataUpdateFails() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		when(wordPressPostService.post(any(SiteContents.class))).thenAnswer(invocation -> {
			SiteContents target = invocation.getArgument(0);
			target.setCmsContentId(991L);
			throw new IllegalStateException("metadata update failed");
		});

		SiteInfoProcessPool pool = processingPool(category2);
		SiteContents contents = siteContentsRepository.save(
				createPostTargetContents(category2, "http://post-meta-fail", "post title", "post body"));

		siteContentsService.saveAllProcessPools(List.of(pool));

		SiteContents saved = siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow();
		assertEquals(991L, saved.getCmsContentId());
		assertEquals(ProcessStatus.FAIL, saved.getProcessStatus());
	}

	@Test
	void testSaveAllProcessPoolsRetriesMetadataForFailedExistingPost() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		SiteInfoProcessPool pool = processingPool(category2);
		SiteContents contents = createPostTargetContents(category2,
				"http://post-meta-retry", "post title", "post body");
		contents.setCmsContentId(992L);
		contents.setProcessStatus(ProcessStatus.FAIL);
		contents = siteContentsRepository.save(contents);

		siteContentsService.saveAllProcessPools(List.of(pool));

		verify(wordPressPostService).updateSourceMetadata(any(SiteContents.class));
		assertEquals(ProcessStatus.SUCCESS,
				siteContentsRepository.findById(contents.getSiteContentsId()).orElseThrow().getProcessStatus());
	}

	@Test
	void testSaveAllProcessPoolsContinuesWithNextPoolAfterCmsFailure() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		when(wordPressPostService.post(any(SiteContents.class)))
				.thenThrow(new RuntimeException("first post failed"))
				.thenReturn(456L);

		SiteInfoProcessPool firstPool = new SiteInfoProcessPool();
		firstPool.setProcessStatus(ProcessStatus.PROCESSING);
		firstPool.setProcessId("job-manual");
		firstPool.setProcessTime(LocalDateTime.now());
		firstPool.setSiteCategory(category1);
		firstPool = siteInfoProcessRepository.save(firstPool);

		SiteInfoProcessPool secondPool = new SiteInfoProcessPool();
		secondPool.setProcessStatus(ProcessStatus.PROCESSING);
		secondPool.setProcessId("job-manual");
		secondPool.setProcessTime(LocalDateTime.now());
		secondPool.setSiteCategory(category2);
		secondPool = siteInfoProcessRepository.save(secondPool);

		SiteContents failedContents = siteContentsRepository.save(
				createPostTargetContents(category1, "http://post-first-fail", "first title", "first body"));
		SiteContents successfulContents = siteContentsRepository.save(
				createPostTargetContents(category2, "http://post-second-success", "second title", "second body"));

		List<SiteInfoProcessPool> result = siteContentsService.saveAllProcessPools(List.of(firstPool, secondPool));

		assertEquals(2, result.size());
		SiteInfoProcessPool firstReleased = siteInfoProcessRepository.findById(firstPool.getSiteInfoProcessId()).orElseThrow();
		SiteInfoProcessPool secondReleased = siteInfoProcessRepository.findById(secondPool.getSiteInfoProcessId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, firstReleased.getProcessStatus());
		assertEquals(ProcessStatus.FAIL, firstReleased.getLastResultStatus());
		assertEquals(ProcessStatus.NONE, secondReleased.getProcessStatus());
		assertEquals(ProcessStatus.SUCCESS, secondReleased.getLastResultStatus());
		assertEquals(ProcessStatus.FAIL, siteContentsRepository.findById(failedContents.getSiteContentsId())
				.orElseThrow().getProcessStatus());
		SiteContents posted = siteContentsRepository.findById(successfulContents.getSiteContentsId()).orElseThrow();
		assertEquals(ProcessStatus.SUCCESS, posted.getProcessStatus());
		assertEquals(456L, posted.getCmsContentId());
		verify(wordPressPostService, times(2)).post(any(SiteContents.class));
	}

	@Test
	void repairConfiguredCmsContentPropagatesSpringDataFailure() throws Exception {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "repairCmsContentId", 2643L);
		SiteContents existing = createPostTargetContents(category2, "https://source.example/db-failure",
				"repair db failure", "body");
		existing.setCmsContentId(2643L);
		existing.setFeaturedImageUrl(null);
		siteContentsRepository.save(existing);
		SiteInfoProcessPool repairPool = processingPool(category2);
		when(contentsParser.loadContents(any(SiteContents.class)))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"));

		assertThrows(DataAccessResourceFailureException.class,
				() -> siteContentsService.repairConfiguredCmsContent(List.of(repairPool)));
	}

	@Test
	void categorySyncPropagatesTransactionFailure() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		SiteContents linked = createPostTargetContents(category2, "https://source.example/linked", "linked", "body");
		linked.setCmsContentId(10L);
		siteContentsRepository.save(linked);
		SiteInfoProcessPool processingPool = processingPool(category2);
		org.mockito.Mockito.doThrow(new TransactionSystemException("transaction failed"))
				.when(wordPressPostService).updateCategories(any(SiteContents.class));

		assertThrows(TransactionSystemException.class,
				() -> siteContentsService.saveAllProcessPools(List.of(processingPool)));
	}

	@Test
	void postLimitIsOneRunWideBudgetAcrossMultipleCategories() {
		ReflectionTestUtils.setField(siteContentsService, "skipCmsPost", false);
		ReflectionTestUtils.setField(siteContentsService, "postContentsLimitCount", 2);

		SiteInfoProcessPool firstPool = processingPool(category1);
		SiteInfoProcessPool secondPool = processingPool(category2);
		siteContentsRepository.save(createPostTargetContents(category1, "http://post-budget-first", "first", "body"));
		siteContentsRepository.save(createPostTargetContents(category2, "http://post-budget-second", "second", "body"));
		siteContentsRepository.save(createPostTargetContents(category2, "http://post-budget-third", "third", "body"));
		when(wordPressPostService.post(any(SiteContents.class))).thenReturn(100L, 200L, 300L);

		siteContentsService.saveAllProcessPools(List.of(firstPool, secondPool));

		verify(wordPressPostService, times(2)).post(any(SiteContents.class));
		assertEquals(2, crawlJobSummary.snapshot().wordpressPostAttemptCount());
	}

	private SiteInfoProcessPool processingPool(SiteCategory category) {
		SiteInfoProcessPool pool = new SiteInfoProcessPool();
		pool.setProcessStatus(ProcessStatus.PROCESSING);
		pool.setProcessId("job-manual");
		pool.setProcessTime(LocalDateTime.now());
		pool.setSiteCategory(category);
		return siteInfoProcessRepository.save(pool);
	}

	private SiteContents createPostTargetContents(SiteCategory category, String url, String title, String body) {
		SiteContents contents = new SiteContents();
		contents.setProcessStatus(ProcessStatus.NONE);
		contents.setUrl(url);
		contents.setTitle(title);
		contents.setContents(body);
		contents.setSiteCategory(category);
		return contents;
	}

}
