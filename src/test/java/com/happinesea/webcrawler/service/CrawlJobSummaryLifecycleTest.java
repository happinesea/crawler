package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;

class CrawlJobSummaryLifecycleTest {

    @Test
    void completedJobWithoutFailuresKeepsCompletedExitStatus() {
        CrawlJobSummary summary = new CrawlJobSummary();
        JobExecution jobExecution = completedExecution(101L);

        summary.beforeJob(jobExecution);
        summary.setTargetCategoryCount(1);
        summary.incrementCrawlSuccess();
        summary.incrementWordPressPostSuccess();
        summary.afterJob(jobExecution);

        assertEquals(ExitStatus.COMPLETED.getExitCode(), jobExecution.getExitStatus().getExitCode());
        assertEquals(0, summary.totalFailures());
    }

    @Test
    void existingFailedBatchStatusRemainsFailed() {
        CrawlJobSummary summary = new CrawlJobSummary();
        JobExecution jobExecution = new JobExecution(102L);
        jobExecution.setStatus(BatchStatus.FAILED);
        jobExecution.setExitStatus(ExitStatus.FAILED);

        summary.beforeJob(jobExecution);
        summary.incrementCrawlFailure();
        summary.incrementWordPressPostFailure();
        summary.afterJob(jobExecution);

        assertEquals(ExitStatus.FAILED.getExitCode(), jobExecution.getExitStatus().getExitCode());
        assertEquals(2, summary.totalFailures());
    }

    @Test
    void beforeJobResetsCountersFromPreviousExecution() {
        CrawlJobSummary summary = new CrawlJobSummary();
        summary.setTargetCategoryCount(9);
        summary.incrementCrawlFailure();
        summary.incrementAiFailure();
        summary.incrementWordPressPostFailure();

        summary.beforeJob(completedExecution(103L));

        CrawlJobSummary.Snapshot snapshot = summary.snapshot();
        assertEquals(0, snapshot.targetCategoryCount());
        assertEquals(0, snapshot.crawlFailureCount());
        assertEquals(0, snapshot.aiFailureCount());
        assertEquals(0, snapshot.wordpressPostFailureCount());
        assertEquals(0, summary.totalFailures());
    }

    @Test
    void countersRemainAccurateDuringParallelCategoryProcessing() {
        CrawlJobSummary summary = new CrawlJobSummary();
        int categoryCount = 1_000;

        IntStream.range(0, categoryCount).parallel().forEach(index -> {
            summary.incrementCrawlSuccess();
            summary.addNewSaved(1);
            if (index % 10 == 0) {
                summary.incrementAiFallback();
            }
        });

        CrawlJobSummary.Snapshot snapshot = summary.snapshot();
        assertEquals(categoryCount, snapshot.crawlSuccessCount());
        assertEquals(categoryCount, snapshot.newSavedCount());
        assertEquals(100, snapshot.aiFallbackCount());
    }

    private JobExecution completedExecution(Long id) {
        JobExecution jobExecution = new JobExecution(id);
        jobExecution.setStatus(BatchStatus.COMPLETED);
        jobExecution.setExitStatus(ExitStatus.COMPLETED);
        return jobExecution;
    }
}
