package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionSystemException;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.ContentsParser;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteContentsRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;

import jakarta.persistence.EntityManager;

@ExtendWith(MockitoExtension.class)
class SiteContentsServiceFatalFailureTest {

	@Mock
	private EntityManager entityManager;
	@Mock
	private SiteContentsRepository siteContentsRepository;
	@Mock
	private SiteInfoProcessRepository siteInfoProcessRepository;
	@Mock
	private WordPressPostService wordPressPostService;
	@Mock
	private AiAnalysisService aiAnalysisService;
	@Mock
	private ProcessPoolHeartbeatManager heartbeatManager;
	@Mock
	private SiteContentsInsertService siteContentsInsertService;
	@Mock
	private UrlCanonicalizer urlCanonicalizer;
	@Mock
	private ContentsParser contentsParser;

	private SiteContentsService service;
	private CrawlJobSummary summary;

	@BeforeEach
	void setUp() {
		service = new SiteContentsService();
		summary = new CrawlJobSummary();
		ReflectionTestUtils.setField(service, "entityManager", entityManager);
		ReflectionTestUtils.setField(service, "siteContentsRepository", siteContentsRepository);
		ReflectionTestUtils.setField(service, "siteInfoProcessRepository", siteInfoProcessRepository);
		ReflectionTestUtils.setField(service, "wordPressPostService", wordPressPostService);
		ReflectionTestUtils.setField(service, "aiAnalysisService", aiAnalysisService);
		ReflectionTestUtils.setField(service, "crawlJobSummary", summary);
		ReflectionTestUtils.setField(service, "heartbeatManager", heartbeatManager);
		ReflectionTestUtils.setField(service, "siteContentsInsertService", siteContentsInsertService);
		ReflectionTestUtils.setField(service, "urlCanonicalizer", urlCanonicalizer);
		ReflectionTestUtils.setField(service, "contentsParser", contentsParser);
		ReflectionTestUtils.setField(service, "postContentsLimitCount", 10);
		ReflectionTestUtils.setField(service, "postFeaturedImageOnly", false);
		ReflectionTestUtils.setField(service, "skipCmsPost", false);
		ReflectionTestUtils.setField(service, "repairCmsContentId", null);
		when(siteContentsRepository.findCmsLinkedContentsWithBlankDescription()).thenReturn(List.of());
		when(siteContentsRepository.findCmsLinkedContents(any(SiteCategory.class))).thenReturn(List.of());
	}

	@Test
	void wordpressAttemptThenDatabaseFailureConsumesReservationAndStopsLaterCategories() {
		SiteCategory firstCategory = category(1);
		SiteCategory secondCategory = category(2);
		SiteContents firstContents = contents(firstCategory, 11);
		SiteContents secondContents = contents(secondCategory, 22);
		when(siteContentsRepository.findContents4Post(any(SiteCategory.class), anyList()))
				.thenReturn(List.of(firstContents), List.of(secondContents));
		when(aiAnalysisService.prepareForPost(any(SiteContents.class))).thenAnswer(invocation -> invocation.getArgument(0));
		when(wordPressPostService.post(firstContents)).thenReturn(100L);
		when(siteContentsRepository.save(firstContents))
				.thenThrow(new DataAccessResourceFailureException("database unavailable"))
				.thenReturn(firstContents);

		assertThrows(DataAccessResourceFailureException.class,
				() -> service.saveAllProcessPools(List.of(pool(firstCategory, 1), pool(secondCategory, 2))));

		assertEquals(1, summary.snapshot().wordpressPostAttemptCount());
		verify(wordPressPostService).post(firstContents);
		verify(wordPressPostService, never()).post(secondContents);
	}

	@Test
	void wordpressPostPropagatesTransactionFailure() {
		SiteCategory category = category(1);
		SiteContents contents = contents(category, 11);
		when(siteContentsRepository.findContents4Post(any(SiteCategory.class), anyList()))
				.thenReturn(List.of(contents));
		when(aiAnalysisService.prepareForPost(contents)).thenReturn(contents);
		when(wordPressPostService.post(contents)).thenThrow(new TransactionSystemException("transaction failed"));

		assertThrows(TransactionSystemException.class,
				() -> service.saveAllProcessPools(List.of(pool(category, 1))));

		assertEquals(1, summary.snapshot().wordpressPostAttemptCount());
	}

	private SiteCategory category(int id) {
		SiteCategory category = new SiteCategory();
		category.setSiteCategoryId(id);
		return category;
	}

	private SiteContents contents(SiteCategory category, int id) {
		SiteContents contents = new SiteContents();
		contents.setSiteContentsId((long) id);
		contents.setSiteCategory(category);
		contents.setProcessStatus(ProcessStatus.NONE);
		contents.setTitle("title-" + id);
		contents.setContents("body-" + id);
		contents.setDescription("description-" + id);
		contents.setUrl("https://example.com/" + id);
		return contents;
	}

	private SiteInfoProcessPool pool(SiteCategory category, int id) {
		SiteInfoProcessPool pool = new SiteInfoProcessPool();
		pool.setSiteInfoProcessId(id);
		pool.setSiteCategory(category);
		pool.setProcessStatus(ProcessStatus.PROCESSING);
		pool.setProcessId("job-test/worker-" + id);
		return pool;
	}
}
