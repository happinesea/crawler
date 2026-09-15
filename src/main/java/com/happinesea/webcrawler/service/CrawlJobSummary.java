package com.happinesea.webcrawler.service;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CrawlJobSummary implements JobExecutionListener {
	private final AtomicInteger targetCategoryCount = new AtomicInteger();
	private final AtomicInteger crawlSuccessCount = new AtomicInteger();
	private final AtomicInteger crawlFailureCount = new AtomicInteger();
	private final AtomicInteger newSavedCount = new AtomicInteger();
	private final AtomicInteger duplicateSkippedCount = new AtomicInteger();
	private final AtomicInteger aiSuccessCount = new AtomicInteger();
	private final AtomicInteger aiFallbackCount = new AtomicInteger();
	private final AtomicInteger aiFailureCount = new AtomicInteger();
	private final AtomicInteger wordpressPostSuccessCount = new AtomicInteger();
	private final AtomicInteger wordpressPostFailureCount = new AtomicInteger();
	private final AtomicInteger wordpressPostAttemptCount = new AtomicInteger();

	@Override
	public void beforeJob(JobExecution jobExecution) {
		reset();
	}

	@Override
	public void afterJob(JobExecution jobExecution) {
		log.info("crawl_job_summary jobExecutionId={} targetCategoryCount={} crawlSuccessCount={} "
						+ "crawlFailureCount={} newSavedCount={} duplicateSkippedCount={} aiSuccessCount={} "
						+ "aiFallbackCount={} aiFailureCount={} wordpressPostSuccessCount={} "
						+ "wordpressPostFailureCount={} wordpressPostAttemptCount={}",
				jobExecution.getId(), targetCategoryCount.get(), crawlSuccessCount.get(), crawlFailureCount.get(),
				newSavedCount.get(), duplicateSkippedCount.get(), aiSuccessCount.get(), aiFallbackCount.get(),
				aiFailureCount.get(), wordpressPostSuccessCount.get(), wordpressPostFailureCount.get(),
				wordpressPostAttemptCount.get());
	}

	public void reset() {
		targetCategoryCount.set(0);
		crawlSuccessCount.set(0);
		crawlFailureCount.set(0);
		newSavedCount.set(0);
		duplicateSkippedCount.set(0);
		aiSuccessCount.set(0);
		aiFallbackCount.set(0);
		aiFailureCount.set(0);
		wordpressPostSuccessCount.set(0);
		wordpressPostFailureCount.set(0);
		wordpressPostAttemptCount.set(0);
	}

	public void setTargetCategoryCount(int value) {
		targetCategoryCount.set(Math.max(0, value));
	}

	public void incrementCrawlSuccess() {
		crawlSuccessCount.incrementAndGet();
	}

	public void incrementCrawlFailure() {
		crawlFailureCount.incrementAndGet();
	}

	public void addNewSaved(int value) {
		newSavedCount.addAndGet(Math.max(0, value));
	}

	public void addDuplicateSkipped(int value) {
		duplicateSkippedCount.addAndGet(Math.max(0, value));
	}

	public void incrementAiSuccess() {
		aiSuccessCount.incrementAndGet();
	}

	public void incrementAiFallback() {
		aiFallbackCount.incrementAndGet();
	}

	public void incrementAiFailure() {
		aiFailureCount.incrementAndGet();
	}

	public void incrementWordPressPostSuccess() {
		wordpressPostSuccessCount.incrementAndGet();
	}

	public void incrementWordPressPostFailure() {
		wordpressPostFailureCount.incrementAndGet();
	}

	public boolean tryReserveWordPressPostAttempt(int maximumAttempts) {
		if (maximumAttempts <= 0) {
			return false;
		}
		while (true) {
			int current = wordpressPostAttemptCount.get();
			if (current >= maximumAttempts) {
				return false;
			}
			if (wordpressPostAttemptCount.compareAndSet(current, current + 1)) {
				return true;
			}
		}
	}

	public int totalFailures() {
		return crawlFailureCount.get() + aiFailureCount.get() + wordpressPostFailureCount.get();
	}

	public Snapshot snapshot() {
		return new Snapshot(targetCategoryCount.get(), crawlSuccessCount.get(), crawlFailureCount.get(),
				newSavedCount.get(), duplicateSkippedCount.get(), aiSuccessCount.get(), aiFallbackCount.get(),
				aiFailureCount.get(), wordpressPostSuccessCount.get(), wordpressPostFailureCount.get(),
				wordpressPostAttemptCount.get());
	}

	public record Snapshot(int targetCategoryCount, int crawlSuccessCount, int crawlFailureCount,
			int newSavedCount, int duplicateSkippedCount, int aiSuccessCount, int aiFallbackCount,
			int aiFailureCount, int wordpressPostSuccessCount, int wordpressPostFailureCount,
			int wordpressPostAttemptCount) {
	}
}
