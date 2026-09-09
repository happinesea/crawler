package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.boot.autoconfigure.batch.JobExecutionEvent;
import org.springframework.boot.autoconfigure.batch.JobExecutionExitCodeGenerator;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

class CrawlJobSummaryTest {
	@Test
	void summaryInfoIncludesWordPressPostAttemptCount() {
		CrawlJobSummary summary = new CrawlJobSummary();
		JobExecution jobExecution = new JobExecution(99L);
		jobExecution.setStatus(BatchStatus.COMPLETED);
		Logger logger = (Logger) LoggerFactory.getLogger(CrawlJobSummary.class);
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		appender.start();
		logger.addAppender(appender);
		try {
			summary.beforeJob(jobExecution);
			summary.tryReserveWordPressPostAttempt(2);
			summary.tryReserveWordPressPostAttempt(2);

			summary.afterJob(jobExecution);

			assertTrue(appender.list.stream().map(ILoggingEvent::getFormattedMessage)
					.anyMatch(message -> message.contains("wordpressPostAttemptCount=2")));
		} finally {
			logger.detachAppender(appender);
			appender.stop();
		}
	}

	@Test
	void failuresAreReflectedInJobExitStatusAndSummary() {
		CrawlJobSummary summary = new CrawlJobSummary();
		JobExecution jobExecution = new JobExecution(100L);
		jobExecution.setStatus(BatchStatus.COMPLETED);

		summary.beforeJob(jobExecution);
		summary.setTargetCategoryCount(2);
		summary.incrementCrawlSuccess();
		summary.incrementCrawlFailure();
		summary.incrementAiFallback();
		summary.incrementWordPressPostFailure();
		summary.afterJob(jobExecution);

		assertEquals(BatchStatus.FAILED, jobExecution.getStatus());
		assertEquals("FAILED", jobExecution.getExitStatus().getExitCode());
		assertEquals(2, summary.snapshot().targetCategoryCount());
		assertEquals(2, summary.totalFailures());

		JobExecutionExitCodeGenerator exitCodeGenerator = new JobExecutionExitCodeGenerator();
		exitCodeGenerator.onApplicationEvent(new JobExecutionEvent(jobExecution));
		assertNotEquals(0, exitCodeGenerator.getExitCode());
	}
}
