package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CrawlJobSummaryPostAttemptBudgetTest {

	@Test
	void reservesOneRunWidePostAttemptBudgetAcrossCategories() {
		CrawlJobSummary summary = new CrawlJobSummary();

		assertTrue(summary.tryReserveWordPressPostAttempt(2));
		assertTrue(summary.tryReserveWordPressPostAttempt(2));
		assertFalse(summary.tryReserveWordPressPostAttempt(2));
		assertEquals(2, summary.snapshot().wordpressPostAttemptCount());
	}

	@Test
	void neverExceedsPostAttemptBudgetWhenWorkersReserveConcurrently() throws Exception {
		CrawlJobSummary summary = new CrawlJobSummary();
		ExecutorService workers = Executors.newFixedThreadPool(4);
		CountDownLatch start = new CountDownLatch(1);
		var reservations = new ConcurrentLinkedQueue<Boolean>();

		try {
			for (int worker = 0; worker < 4; worker++) {
				workers.submit(() -> {
					start.await();
					for (int attempt = 0; attempt < 3; attempt++) {
						reservations.add(summary.tryReserveWordPressPostAttempt(5));
					}
					return null;
				});
			}
			start.countDown();
			workers.shutdown();
			assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
		} finally {
			workers.shutdownNow();
		}

		assertEquals(5, reservations.stream().filter(Boolean::booleanValue).count());
		assertEquals(5, summary.snapshot().wordpressPostAttemptCount());
	}
}
