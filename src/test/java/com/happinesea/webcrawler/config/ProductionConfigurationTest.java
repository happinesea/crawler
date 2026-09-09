package com.happinesea.webcrawler.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class ProductionConfigurationTest {

	@Test
	void databaseSessionOutlivesTheLongestBatchTransaction() throws IOException {
		var sources = new YamlPropertySourceLoader().load("production",
				new ClassPathResource("application-prod.yml"));

		assertEquals("SET SESSION wait_timeout=3600",
				sources.get(0).getProperty("spring.datasource.hikari.connection-init-sql"));
	}

	@Test
	void logicalWorkerFallbackDoesNotInheritThePostLimit() throws IOException {
		assertEquals("${CRAWL_PROCESS_LIMIT:10}", loadCrawlerProcessLimit("application.yml"));
		assertEquals("${CRAWL_PROCESS_LIMIT:10}", loadCrawlerProcessLimit("application-prod.yml"));
	}

	private Object loadCrawlerProcessLimit(String resource) throws IOException {
		var sources = new YamlPropertySourceLoader().load(resource, new ClassPathResource(resource));
		return sources.get(0).getProperty("web-crawler.crawl-process-limit-count");
	}
}
