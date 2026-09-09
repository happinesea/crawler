package com.happinesea.webcrawler.service;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.happinesea.webcrawler.RunFatalFailure;
import com.happinesea.webcrawler.entity.SiteContents;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AiAnalysisService {
	private static final String MODE_OFF = "off";
	private static final String MODE_DEEPSEEK = "deepseek";
	private static final String MODE_OPENAI = "openai";

	private final OpenAiCompatibleAiClient aiClient;
	private final CrawlJobSummary crawlJobSummary;

	@Value("${web-crawler.ai.mode:off}")
	private String mode;
	@Value("${web-crawler.ai.max-input-chars:12000}")
	private int maxInputChars;
	@Value("${web-crawler.ai.system-prompt:You are an editor preparing crawled web content for WordPress. Return only valid JSON with keys title and content. Rewrite the title and content naturally in Japanese, preserve facts, do not invent details, and do not include markdown code fences.}")
	private String systemPrompt;

	@Autowired
	public AiAnalysisService(OpenAiCompatibleAiClient aiClient, CrawlJobSummary crawlJobSummary) {
		this.aiClient = aiClient;
		this.crawlJobSummary = crawlJobSummary;
	}

	public AiAnalysisService(OpenAiCompatibleAiClient aiClient) {
		this(aiClient, new CrawlJobSummary());
	}

	public SiteContents prepareForPost(SiteContents contents) {
		if (contents == null || isOff()) {
			return contents;
		}
		if (!isSupportedMode()) {
			log.warn("Unsupported AI mode '{}'. Posting original content.", mode);
			crawlJobSummary.incrementAiFallback();
			return contents;
		}
		if (!aiClient.isConfigured(mode)) {
			log.warn("AI mode '{}' is enabled, but AI client is not configured. Posting original content.", mode);
			crawlJobSummary.incrementAiFallback();
			return contents;
		}

		try {
			AiAnalysisResult result = aiClient.analyze(mode, systemPrompt, buildUserPrompt(contents));
			crawlJobSummary.incrementAiSuccess();
			return copyForPost(contents, result);
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			crawlJobSummary.incrementAiFailure();
			crawlJobSummary.incrementAiFallback();
			log.warn("AI analysis failed. Posting original content. siteContentsId={} url={}",
					contents.getSiteContentsId(), contents.getUrl(), e);
			return contents;
		}
	}

	private boolean isOff() {
		return StringUtils.isBlank(mode) || MODE_OFF.equalsIgnoreCase(mode);
	}

	private boolean isSupportedMode() {
		return MODE_DEEPSEEK.equalsIgnoreCase(mode) || MODE_OPENAI.equalsIgnoreCase(mode);
	}

	private String buildUserPrompt(SiteContents contents) {
		String body = StringUtils.defaultString(contents.getContents());
		if (body.length() > maxInputChars) {
			body = body.substring(0, maxInputChars);
		}

		return """
				Analyze this crawled content and prepare WordPress-ready JSON.

				Source URL:
				%s

				Original title:
				%s

				Original content:
				%s
				""".formatted(
				StringUtils.defaultString(contents.getUrl()),
				StringUtils.defaultString(contents.getTitle()),
				body);
	}

	private SiteContents copyForPost(SiteContents original, AiAnalysisResult result) {
		SiteContents copy = new SiteContents();
		copy.setSiteContentsId(original.getSiteContentsId());
		copy.setUrl(original.getUrl());
		copy.setRequestedUrl(original.getRequestedUrl());
		copy.setRedirectedUrl(original.getRedirectedUrl());
		copy.setCanonicalUrl(original.getCanonicalUrl());
		copy.setNormalizedUrl(original.getNormalizedUrl());
		copy.setNormalizedUrlHash(original.getNormalizedUrlHash());
		copy.setSourceUrl(original.getSourceUrl());
		copy.setSiteCategory(original.getSiteCategory());
		copy.setProcessStatus(original.getProcessStatus());
		copy.setDescription(original.getDescription());
		copy.setFeaturedImageUrl(original.getFeaturedImageUrl());
		copy.setCmsContentId(original.getCmsContentId());
		copy.setTitle(StringUtils.isBlank(result.title()) ? original.getTitle() : result.title());
		copy.setContents(StringUtils.isBlank(result.contents()) ? original.getContents() : result.contents());
		return copy;
	}
}
