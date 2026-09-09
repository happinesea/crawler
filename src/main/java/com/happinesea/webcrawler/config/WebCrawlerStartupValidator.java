package com.happinesea.webcrawler.config;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
public class WebCrawlerStartupValidator {
	@Value("${web-crawler.skip-cms-post:true}")
	private boolean skipCmsPost;
	@Value("${web-crawler.ai.mode:off}")
	private String aiMode;
	@Value("${web-crawler.ai.api-key:}")
	private String aiApiKey;
	@Value("${web-crawler.ai.base-url:}")
	private String aiBaseUrl;
	@Value("${web-crawler.ai.model:}")
	private String aiModel;
	@Value("${web-crawler.wordpress.base-url:${web-crawler.host-info:}}")
	private String wordpressBaseUrl;
	@Value("${web-crawler.host-info:}")
	private String hostInfo;
	@Value("${web-crawler.wordpress.username:}")
	private String wordpressUsername;
	@Value("${web-crawler.wordpress.application-password:}")
	private String wordpressApplicationPassword;
	@Value("${web-crawler.processing-timeout-minutes:60}")
	private int processingTimeoutMinutes = 60;
	@Value("${web-crawler.heartbeat-interval-ms:60000}")
	private long heartbeatIntervalMs = 60_000L;

	@PostConstruct
	void validate() {
		long timeoutMs = Math.max(1, processingTimeoutMinutes) * 60_000L;
		if (heartbeatIntervalMs <= 0 || heartbeatIntervalMs * 3 >= timeoutMs) {
			throw new IllegalStateException(
					"web-crawler.heartbeat-interval-ms must be positive and less than one third of the processing timeout.");
		}
		if (isAiEnabled()) {
			require(aiApiKey, "web-crawler.ai.api-key is required when AI is enabled.");
			require(aiBaseUrl, "web-crawler.ai.base-url is required when AI is enabled.");
			require(aiModel, "web-crawler.ai.model is required when AI is enabled.");
		}
		if (!skipCmsPost) {
			require(firstNotBlank(wordpressBaseUrl, hostInfo),
					"web-crawler.wordpress.base-url or web-crawler.host-info is required when CMS post is enabled.");
			require(wordpressUsername, "web-crawler.wordpress.username is required when CMS post is enabled.");
			require(wordpressApplicationPassword,
					"web-crawler.wordpress.application-password is required when CMS post is enabled.");
		}
	}

	private String firstNotBlank(String first, String fallback) {
		return StringUtils.isBlank(first) ? fallback : first;
	}

	private boolean isAiEnabled() {
		return StringUtils.isNotBlank(aiMode) && !"off".equalsIgnoreCase(aiMode);
	}

	private void require(String value, String message) {
		if (StringUtils.isBlank(value)) {
			throw new IllegalStateException(message);
		}
	}
}
