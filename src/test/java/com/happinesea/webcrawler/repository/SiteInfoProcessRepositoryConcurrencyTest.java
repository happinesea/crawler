package com.happinesea.webcrawler.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteInfo;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;

@SpringBootTest
@ActiveProfiles("test")
class SiteInfoProcessRepositoryConcurrencyTest {

    private static final List<ProcessStatus> AVAILABLE_STATUSES =
            List.of(ProcessStatus.NONE, ProcessStatus.FAIL, ProcessStatus.SUCCESS);

    @Autowired
    private SiteInfoProcessRepository processRepository;

    @Autowired
    private SiteCategoryRepository categoryRepository;

    @Autowired
    private SiteInfoRepository siteInfoRepository;

    private ExecutorService executor;
    private SiteCategory category;

    @BeforeEach
    void setUp() {
        processRepository.deleteAll();
        categoryRepository.deleteAll();
        siteInfoRepository.deleteAll();

        SiteInfo siteInfo = siteInfoRepository.save(new SiteInfo());
        SiteCategory newCategory = new SiteCategory();
        newCategory.setCategoryName("concurrency-test");
        newCategory.setSiteInfo(siteInfo);
        category = categoryRepository.save(newCategory);
        executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void onlyOneConcurrentOwnerCanClaimAvailablePool() throws Exception {
        SiteInfoProcessPool pool = savePool(ProcessStatus.NONE, null, LocalDateTime.now());

        ClaimResults results = claimConcurrently(pool.getSiteInfoProcessId());

        assertEquals(1, results.updatedRows());
        SiteInfoProcessPool saved = processRepository.findById(pool.getSiteInfoProcessId()).orElseThrow();
        assertEquals(ProcessStatus.PROCESSING, saved.getProcessStatus());
        assertTrue(Set.of("job-one", "job-two").contains(saved.getProcessId()));
		assertEquals(1, saved.getLeaseAttempt());
    }

    @Test
    void onlyOneConcurrentOwnerCanRecoverStaleProcessingPool() throws Exception {
        SiteInfoProcessPool pool = savePool(
                ProcessStatus.PROCESSING, "job-stopped", LocalDateTime.now().minusMinutes(61));

        ClaimResults results = claimConcurrently(pool.getSiteInfoProcessId());

        assertEquals(1, results.updatedRows());
        SiteInfoProcessPool saved = processRepository.findById(pool.getSiteInfoProcessId()).orElseThrow();
        assertEquals(ProcessStatus.PROCESSING, saved.getProcessStatus());
        assertTrue(Set.of("job-one", "job-two").contains(saved.getProcessId()));
    }

    @Test
    void finishRequiresCurrentOwnerAndProcessingState() {
        SiteInfoProcessPool pool = savePool(ProcessStatus.PROCESSING, "job-current", LocalDateTime.now());
		pool.setClaimedAt(LocalDateTime.now().minusSeconds(1));
		pool.setHeartbeatAt(LocalDateTime.now());
		processRepository.saveAndFlush(pool);
        LocalDateTime finishedAt = LocalDateTime.now();

        int wrongOwner = processRepository.finishOwnedProcessing(
				pool.getSiteInfoProcessId(), "job-old", 0, ProcessStatus.NONE, ProcessStatus.SUCCESS,
				ProcessStatus.PROCESSING, finishedAt);
        int currentOwner = processRepository.finishOwnedProcessing(
				pool.getSiteInfoProcessId(), "job-current", 0, ProcessStatus.NONE, ProcessStatus.SUCCESS,
				ProcessStatus.PROCESSING, finishedAt);
        int alreadyFinished = processRepository.finishOwnedProcessing(
				pool.getSiteInfoProcessId(), "job-current", 0, ProcessStatus.NONE, ProcessStatus.FAIL,
				ProcessStatus.PROCESSING, finishedAt.plusSeconds(1));

        assertEquals(0, wrongOwner);
        assertEquals(1, currentOwner);
        assertEquals(0, alreadyFinished);
		SiteInfoProcessPool released = processRepository.findById(pool.getSiteInfoProcessId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, released.getProcessStatus());
		assertEquals(ProcessStatus.SUCCESS, released.getLastResultStatus());
		assertNull(released.getProcessId());
		assertNull(released.getClaimedAt());
		assertNull(released.getHeartbeatAt());
    }

	@Test
	void staleOwnerCannotReleaseReclaimedLeaseWithSamePoolId() {
		SiteInfoProcessPool pool = savePool(
				ProcessStatus.PROCESSING, "job-old", LocalDateTime.now().minusMinutes(61));
		pool.setLeaseAttempt(3);
		pool.setHeartbeatAt(LocalDateTime.now().minusMinutes(61));
		processRepository.saveAndFlush(pool);

		LocalDateTime now = LocalDateTime.now();
		assertEquals(1, processRepository.claimForProcessing(pool.getSiteInfoProcessId(), "job-new", "instance-new",
				now, AVAILABLE_STATUSES, ProcessStatus.PROCESSING, now.minusMinutes(60)));
		assertEquals(0, processRepository.finishOwnedProcessing(pool.getSiteInfoProcessId(), "job-old", 3,
				ProcessStatus.NONE, ProcessStatus.FAIL, ProcessStatus.PROCESSING, now.plusSeconds(1)));
		assertEquals(1, processRepository.finishOwnedProcessing(pool.getSiteInfoProcessId(), "job-new", 4,
				ProcessStatus.NONE, ProcessStatus.SUCCESS, ProcessStatus.PROCESSING, now.plusSeconds(2)));

		SiteInfoProcessPool released = processRepository.findById(pool.getSiteInfoProcessId()).orElseThrow();
		assertEquals(ProcessStatus.NONE, released.getProcessStatus());
		assertEquals(ProcessStatus.SUCCESS, released.getLastResultStatus());
	}

	@Test
	void heartbeatRequiresCurrentOwnerAndAttempt() {
		SiteInfoProcessPool pool = savePool(ProcessStatus.PROCESSING, "job-current", LocalDateTime.now());
		pool.setLeaseAttempt(7);
		processRepository.saveAndFlush(pool);

		LocalDateTime heartbeatAt = LocalDateTime.now().plusSeconds(1);
		assertEquals(0, processRepository.heartbeatOwnedProcessing(pool.getSiteInfoProcessId(), "job-current", 6,
				ProcessStatus.PROCESSING, heartbeatAt));
		assertEquals(1, processRepository.heartbeatOwnedProcessing(pool.getSiteInfoProcessId(), "job-current", 7,
				ProcessStatus.PROCESSING, heartbeatAt));
		assertEquals(heartbeatAt.withNano(0),
				processRepository.findById(pool.getSiteInfoProcessId()).orElseThrow().getHeartbeatAt().withNano(0));
	}

    private ClaimResults claimConcurrently(Integer poolId) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        Future<Integer> first = executor.submit(claim(poolId, "job-one", ready, start));
        Future<Integer> second = executor.submit(claim(poolId, "job-two", ready, start));

        assertTrue(ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        return new ClaimResults(
                first.get(10, TimeUnit.SECONDS),
                second.get(10, TimeUnit.SECONDS));
    }

    private Callable<Integer> claim(Integer poolId, String owner, CountDownLatch ready, CountDownLatch start) {
        return () -> {
            ready.countDown();
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent claim start");
            }
            LocalDateTime now = LocalDateTime.now();
            return processRepository.claimForProcessing(
					poolId, owner, owner, now, AVAILABLE_STATUSES,
                    ProcessStatus.PROCESSING, now.minusMinutes(60));
        };
    }

    private SiteInfoProcessPool savePool(ProcessStatus status, String owner, LocalDateTime processTime) {
        SiteInfoProcessPool pool = new SiteInfoProcessPool();
        pool.setSiteCategory(category);
        pool.setProcessStatus(status);
        pool.setProcessId(owner);
        pool.setProcessTime(processTime);
        return processRepository.saveAndFlush(pool);
    }

    private record ClaimResults(int first, int second) {
        int updatedRows() {
            return first + second;
        }
    }
}
