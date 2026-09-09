package com.happinesea.webcrawler.service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.RunFatalFailure;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfo;

import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WordPressPostService {
	private final RestTemplate restTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Configuration templateConfiguration;

	@Value("${web-crawler.wordpress.base-url:${web-crawler.host-info:}}")
	private String baseUrl;
	@Value("${web-crawler.host-info:}")
	private String hostInfo;
	@Value("${web-crawler.wordpress.username:}")
	private String username;
	@Value("${web-crawler.wordpress.application-password:}")
	private String applicationPassword;
	@Value("${web-crawler.wordpress.post-status:publish}")
	private String postStatus;
	@Value("${web-crawler.wordpress.max-image-upload-count:3}")
	private int maxImageUploadCount = 3;

	public WordPressPostService(RestTemplateBuilder restTemplateBuilder,
			@Value("${web-crawler.external-connect-timeout-ms:10000}") int externalConnectTimeoutMs) {
		Duration timeout = Duration.ofMillis(Math.max(1, externalConnectTimeoutMs));
		this.restTemplate = restTemplateBuilder
				.setConnectTimeout(timeout)
				.setReadTimeout(timeout)
				.build();
		this.templateConfiguration = new Configuration(Configuration.VERSION_2_3_34);
		this.templateConfiguration.setClassLoaderForTemplateLoading(getClass().getClassLoader(), "templates");
		this.templateConfiguration.setDefaultEncoding(StandardCharsets.UTF_8.name());
		this.templateConfiguration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
	}

	public Long post(SiteContents contents) {
		validateConfigured();
		if (contents == null) {
			throw new IllegalArgumentException("contents is required.");
		}

		String normalizedBaseUrl = normalizeBaseUrl(resolveBaseUrl());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken());
		if (ProcessStatus.NONE.equals(contents.getProcessStatus()) || ProcessStatus.FAIL.equals(contents.getProcessStatus())) {
			Long existingPostId = findExistingPostId(contents, normalizedBaseUrl, headers);
			if (existingPostId != null) {
				return existingPostId;
			}
		}
		ProcessedPostBody processedBody = processArticleBodyImages(contents, normalizedBaseUrl);
		String postContent = renderPostContent(contents, processedBody.html());
		Map<String, Object> sourceMetadata = resolveSourceMetadata(contents);
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("title", contents.getTitle());
		body.put("content", postContent);
		body.put("status", sourceMetadata == null ? postStatus : "draft");
		if (processedBody.featuredMediaId() != null) {
			body.put("featured_media", processedBody.featuredMediaId());
		}
		List<Integer> categories = resolveCategories(contents);
		if (!categories.isEmpty()) {
			body.put("categories", categories);
		}
		ResponseEntity<String> response;
		try {
			response = restTemplate.postForEntity(normalizedBaseUrl + "/wp-json/wp/v2/posts",
					new HttpEntity<>(body, headers), String.class);
		} catch (ResourceAccessException e) {
			try {
				Long reconciledPostId = findExistingPostId(contents, normalizedBaseUrl, headers);
				if (reconciledPostId != null) {
					return reconciledPostId;
				}
			} catch (RuntimeException reconciliationFailure) {
				e.addSuppressed(reconciliationFailure);
			}
			throw e;
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("WordPress post failed. status=" + e.getStatusCode()
					+ " body=" + safeResponseBody(e.getResponseBodyAsString()), e);
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress post failed. status=" + response.getStatusCode()
					+ " body=" + safeResponseBody(response.getBody()));
		}

		Long cmsContentId = parsePostId(response.getBody());
		if (cmsContentId == null) {
			throw new IllegalStateException("WordPress post response does not contain id. body="
					+ safeResponseBody(response.getBody()));
		}
		contents.setCmsContentId(cmsContentId);
		updateSourceMetadataRequest(contents, sourceMetadata, cmsContentId, normalizedBaseUrl, headers);
		log.info("Posted content to WordPress. siteContentsId={} cmsContentId={} url={}",
				contents.getSiteContentsId(), cmsContentId, contents.getUrl());
		return cmsContentId;
	}

	private Long findExistingPostId(SiteContents contents, String normalizedBaseUrl, HttpHeaders headers) {
		String sourceUrl = contents.getUrl();
		if (StringUtils.isBlank(sourceUrl)) {
			return null;
		}
		String searchUrl = UriComponentsBuilder.fromUriString(normalizedBaseUrl + "/wp-json/wp/v2/posts")
				.queryParam("search", sourceUrl)
				.queryParam("per_page", 100)
				.queryParam("context", "edit")
				.queryParam("_fields", "id,content")
				.build()
				.encode()
				.toUriString();
		ResponseEntity<String> response = restTemplate.exchange(searchUrl, HttpMethod.GET,
				new HttpEntity<>(headers), String.class);
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress post reconciliation failed. status=" + response.getStatusCode());
		}
		try {
			List<Long> matchingPostIds = new ArrayList<>();
			for (JsonNode post : objectMapper.readTree(response.getBody())) {
				String content = firstNotBlank(post.path("content").path("raw").asText(null),
						post.path("content").path("rendered").asText(null));
				if (Jsoup.parseBodyFragment(firstNotBlank(content, "")).select("a[href]").stream()
						.anyMatch(link -> sourceUrl.equals(link.attr("href")))) {
					matchingPostIds.add(post.path("id").asLong());
				}
			}
			if (matchingPostIds.size() > 1) {
				throw new IllegalStateException("Multiple WordPress posts match source URL. siteContentsId="
						+ contents.getSiteContentsId() + " count=" + matchingPostIds.size());
			}
			if (matchingPostIds.size() == 1) {
				return matchingPostIds.get(0);
			}
			return null;
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse WordPress post reconciliation response.", e);
		}
	}

	public void updateCategories(SiteContents contents) {
		validateConfigured();
		if (contents == null) {
			throw new IllegalArgumentException("contents is required.");
		}
		if (contents.getCmsContentId() == null) {
			throw new IllegalArgumentException("contents.cmsContentId is required.");
		}
		List<Integer> categories = resolveCategories(contents);
		if (categories.isEmpty()) {
			log.info("Skip WordPress category update because no category is configured. siteContentsId={} cmsContentId={}",
					contents.getSiteContentsId(), contents.getCmsContentId());
			return;
		}

		String normalizedBaseUrl = normalizeBaseUrl(resolveBaseUrl());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken());

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("categories", categories);

		ResponseEntity<String> response;
		try {
			response = restTemplate.postForEntity(
					normalizedBaseUrl + "/wp-json/wp/v2/posts/" + contents.getCmsContentId(),
					new HttpEntity<>(body, headers), String.class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("WordPress category update failed. status=" + e.getStatusCode()
					+ " cmsContentId=" + contents.getCmsContentId() + " body="
					+ safeResponseBody(e.getResponseBodyAsString()), e);
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress category update failed. status=" + response.getStatusCode()
					+ " cmsContentId=" + contents.getCmsContentId() + " body="
					+ safeResponseBody(response.getBody()));
		}
		log.info("Updated WordPress categories. siteContentsId={} cmsContentId={} categories={}",
				contents.getSiteContentsId(), contents.getCmsContentId(), categories);
	}

	public boolean repairImagePolicyIfNeeded(SiteContents contents) {
		validateConfigured();
		if (contents == null) {
			throw new IllegalArgumentException("contents is required.");
		}
		if (contents.getCmsContentId() == null) {
			throw new IllegalArgumentException("contents.cmsContentId is required.");
		}
		String normalizedBaseUrl = normalizeBaseUrl(resolveBaseUrl());
		ImageRepairAction repairAction = resolveImageRepairAction(contents, normalizedBaseUrl);
		if (ImageRepairAction.NONE.equals(repairAction)) {
			return false;
		}

		Map<String, Object> body = new LinkedHashMap<>();
		if (ImageRepairAction.FEATURED_ONLY.equals(repairAction)) {
			UploadedMedia featuredMedia = uploadFeaturedImageIfConfigured(contents, normalizedBaseUrl,
					contents.getCmsContentId());
			if (featuredMedia == null) {
				throw new IllegalStateException("CMS repair could not upload the selected featured image. "
						+ "siteContentsId=" + contents.getSiteContentsId());
			}
			body.put("featured_media", featuredMedia.id());
		} else {
			ProcessedPostBody processedBody = processArticleBodyImages(contents, normalizedBaseUrl,
					contents.getCmsContentId());
			body.put("content", renderPostContent(contents, processedBody.html()));
			body.put("featured_media", processedBody.featuredMediaId() == null ? 0 : processedBody.featuredMediaId());
		}
		if (ImageRepairAction.FULL.equals(repairAction)) {
			List<Integer> categories = resolveCategories(contents);
			if (!categories.isEmpty()) {
				body.put("categories", categories);
			}
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken());
		ResponseEntity<String> response;
		try {
			response = restTemplate.postForEntity(
					normalizedBaseUrl + "/wp-json/wp/v2/posts/" + contents.getCmsContentId(),
					new HttpEntity<>(body, headers), String.class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("WordPress image policy repair failed. status=" + e.getStatusCode()
					+ " cmsContentId=" + contents.getCmsContentId() + " body="
					+ safeResponseBody(e.getResponseBodyAsString()), e);
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress image policy repair failed. status=" + response.getStatusCode()
					+ " cmsContentId=" + contents.getCmsContentId() + " body="
					+ safeResponseBody(response.getBody()));
		}
		log.info("Repaired WordPress image policy. siteContentsId={} cmsContentId={}",
				contents.getSiteContentsId(), contents.getCmsContentId());
		return true;
	}

	private String resolvePostBody(SiteContents contents) {
		SiteInfo siteInfo = contents.getSiteInfo();
		if (siteInfo == null || !siteInfo.allowsFullContent()) {
			return firstNotBlank(contents.getDescription(), contents.getTitle());
		}
		return firstNotBlank(contents.getContents(), firstNotBlank(contents.getDescription(), contents.getTitle()));
	}

	public void updateSourceMetadata(SiteContents contents) {
		validateConfigured();
		if (contents == null) {
			throw new IllegalArgumentException("contents is required.");
		}
		if (contents.getCmsContentId() == null) {
			throw new IllegalArgumentException("contents.cmsContentId is required.");
		}
		String normalizedBaseUrl = normalizeBaseUrl(resolveBaseUrl());
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken());
		updateSourceMetadataRequest(contents, resolveSourceMetadata(contents), contents.getCmsContentId(),
				normalizedBaseUrl, headers);
	}

	private Map<String, Object> resolveSourceMetadata(SiteContents contents) {
		SiteInfo siteInfo = contents.getSiteInfo();
		if (siteInfo == null || siteInfo.getSiteInfoId() == null) {
			log.warn("Omit WordPress source metadata because SiteInfo id is unavailable. siteContentsId={}",
					contents.getSiteContentsId());
			return null;
		}
		String contractType = siteInfo.getContractType();
		String sourceUrl = firstNotBlank(contents.getSourceUrl(), contents.getUrl());
		String siteName = siteInfo.getSiteName();
		Map<String, Object> metadata = new LinkedHashMap<>();
		metadata.put("source_site_info_id", siteInfo.getSiteInfoId());
		metadata.put("source_contract_type", contractType == null ? "" : contractType);
		metadata.put("source_url", sourceUrl == null ? "" : sourceUrl);
		metadata.put("source_site", siteName == null ? "" : siteName);
		return metadata;
	}

	private void updateSourceMetadataRequest(SiteContents contents, Map<String, Object> sourceMetadata,
			Long cmsContentId, String normalizedBaseUrl, HttpHeaders headers) {
		if (sourceMetadata == null) {
			return;
		}
		Map<String, Object> body = Map.of("meta", sourceMetadata);
		ResponseEntity<String> response;
		try {
			response = restTemplate.postForEntity(
					normalizedBaseUrl + "/wp-json/wp/v2/posts/" + cmsContentId,
					new HttpEntity<>(body, headers), String.class);
		} catch (RestClientResponseException e) {
			if (e.getStatusCode().value() == 400) {
				finalizePost(cmsContentId, normalizedBaseUrl, headers);
				log.warn("WordPress rejected source metadata; finalized legacy post. siteContentsId={} cmsContentId={} body={}",
						contents.getSiteContentsId(), cmsContentId, safeResponseBody(e.getResponseBodyAsString()));
				return;
			}
			throw new IllegalStateException("WordPress source metadata update failed. status=" + e.getStatusCode()
					+ " cmsContentId=" + cmsContentId + " body="
					+ safeResponseBody(e.getResponseBodyAsString()), e);
		} catch (RestClientException e) {
			throw new IllegalStateException("WordPress source metadata update failed. cmsContentId=" + cmsContentId, e);
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress source metadata update failed. status="
					+ response.getStatusCode() + " cmsContentId=" + cmsContentId + " body="
					+ safeResponseBody(response.getBody()));
		}
		finalizePost(cmsContentId, normalizedBaseUrl, headers);
	}

	private void finalizePost(Long cmsContentId, String normalizedBaseUrl, HttpHeaders headers) {
		ResponseEntity<String> response;
		try {
			response = restTemplate.postForEntity(
					normalizedBaseUrl + "/wp-json/wp/v2/posts/" + cmsContentId,
					new HttpEntity<>(Map.of("status", postStatus), headers), String.class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("WordPress post finalization failed. status=" + e.getStatusCode()
					+ " cmsContentId=" + cmsContentId + " body="
					+ safeResponseBody(e.getResponseBodyAsString()), e);
		} catch (RestClientException e) {
			throw new IllegalStateException("WordPress post finalization failed. cmsContentId="
					+ cmsContentId, e);
		}
		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress post finalization failed. status="
					+ response.getStatusCode() + " cmsContentId=" + cmsContentId + " body="
					+ safeResponseBody(response.getBody()));
		}
	}

	private ProcessedPostBody processArticleBodyImages(SiteContents contents, String normalizedBaseUrl) {
		return processArticleBodyImages(contents, normalizedBaseUrl, null);
	}

	private ProcessedPostBody processArticleBodyImages(SiteContents contents, String normalizedBaseUrl,
			Long repairParentPostId) {
		String body = resolvePostBody(contents);
		if (StringUtils.isBlank(body)) {
			return new ProcessedPostBody(body, null);
		}

		Document document = Jsoup.parseBodyFragment(body, firstNotBlank(contents.getUrl(), ""));
		Map<String, UploadedMedia> uploadedBySource = new HashMap<>();
		UploadedMedia featuredMedia = uploadFeaturedImageIfConfigured(contents, normalizedBaseUrl,
				repairParentPostId);
		Long featuredMediaId = featuredMedia == null ? null : featuredMedia.id();
		if (featuredMedia != null) {
			uploadedBySource.put(normalizeComparableUrl(contents.getFeaturedImageUrl()), featuredMedia);
		}
		boolean useBodyImageAsFeatured = !isSourceWordPress(contents);
		int uploadedImageCount = 0;
		for (Element image : document.select("img")) {
			String sourceUrl = resolveImageUrl(image, contents);
			if (StringUtils.isBlank(sourceUrl)) {
				image.remove();
				continue;
			}
			String comparableSourceUrl = normalizeComparableUrl(sourceUrl);
			UploadedMedia uploadedMedia = uploadedBySource.get(comparableSourceUrl);
			if (uploadedMedia == null) {
				if (uploadedImageCount >= Math.max(1, maxImageUploadCount)) {
					log.info("Skip image because upload limit is reached. sourceUrl={} maxImageUploadCount={}",
							sourceUrl, maxImageUploadCount);
					image.remove();
					continue;
				}
				try {
					uploadedMedia = uploadImage(sourceUrl, normalizedBaseUrl);
					uploadedBySource.put(comparableSourceUrl, uploadedMedia);
					uploadedImageCount++;
				} catch (RuntimeException e) {
					RunFatalFailure.propagateIfPresent(e);
					log.warn("Skip image because upload failed. sourceUrl={}", sourceUrl, e);
					image.remove();
					continue;
				}
			}
			image.attr("src", uploadedMedia.sourceUrl());
			image.removeAttr("srcset");
			image.removeAttr("data-src");
			image.removeAttr("data-original");
			image.removeAttr("data-lazy-src");
			if (useBodyImageAsFeatured && featuredMediaId == null) {
				featuredMediaId = uploadedMedia.id();
			}
		}
		return new ProcessedPostBody(document.body().html(), featuredMediaId);
	}

	private UploadedMedia uploadFeaturedImageIfConfigured(SiteContents contents, String normalizedBaseUrl) {
		return uploadFeaturedImageIfConfigured(contents, normalizedBaseUrl, null);
	}

	private UploadedMedia uploadFeaturedImageIfConfigured(SiteContents contents, String normalizedBaseUrl,
			Long repairParentPostId) {
		String featuredImageUrl = contents.getFeaturedImageUrl();
		if (isExcludedImage(featuredImageUrl, contents)) {
			return null;
		}
		try {
			return uploadImage(featuredImageUrl, normalizedBaseUrl, repairParentPostId);
		} catch (RuntimeException e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Skip featured image because upload failed. siteContentsId={} sourceUrl={}",
					contents.getSiteContentsId(), featuredImageUrl, e);
			return null;
		}
	}

	private String resolveImageUrl(Element image, SiteContents contents) {
		SiteInfo siteInfo = contents.getSiteInfo();
		String logoUrl = siteInfo == null ? null : siteInfo.getLogoUrl();
		for (String candidate : ArticleImagePolicy.resolveImageCandidates(image)) {
			if (!ArticleImagePolicy.isExcludedImage(candidate, logoUrl, image)) {
				return candidate;
			}
		}
		return null;
	}

	private boolean isExcludedImage(String sourceUrl, SiteContents contents) {
		SiteInfo siteInfo = contents.getSiteInfo();
		return ArticleImagePolicy.isExcludedImage(sourceUrl, siteInfo == null ? null : siteInfo.getLogoUrl());
	}

	private String normalizeComparableUrl(String value) {
		return ArticleImagePolicy.normalizeComparableUrl(value);
	}

	private boolean isSourceWordPress(SiteContents contents) {
		SiteInfo siteInfo = contents.getSiteInfo();
		return siteInfo != null && ContentsType.Wordpress.equals(siteInfo.getContentsType());
	}

	private ImageRepairAction resolveImageRepairAction(SiteContents contents, String normalizedBaseUrl) {
		ResponseEntity<String> response;
		try {
			response = restTemplate.getForEntity(normalizedBaseUrl + "/wp-json/wp/v2/posts/"
					+ contents.getCmsContentId() + "?_fields=id,content,featured_media", String.class);
		} catch (RestClientResponseException e) {
			log.warn("Skip WordPress image policy repair check because post fetch failed. status={} cmsContentId={}",
					e.getStatusCode(), contents.getCmsContentId(), e);
			return ImageRepairAction.NONE;
		}
		if (!response.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(response.getBody())) {
			return ImageRepairAction.NONE;
		}
		try {
			JsonNode post = objectMapper.readTree(response.getBody());
			Document document = Jsoup.parseBodyFragment(post.path("content").path("rendered").asText(""));
			for (Element image : document.select("img")) {
				String sourceUrl = resolveImageUrl(image, contents);
				if (isExcludedImage(sourceUrl, contents)) {
					return ImageRepairAction.FULL;
				}
			}
			long featuredMediaId = post.path("featured_media").asLong(0);
			if (featuredMediaId > 0) {
				return isExcludedFeaturedMedia(featuredMediaId, contents, normalizedBaseUrl)
						? ImageRepairAction.FULL : ImageRepairAction.NONE;
			}
			return isExcludedImage(contents.getFeaturedImageUrl(), contents)
					? ImageRepairAction.NONE : ImageRepairAction.FEATURED_ONLY;
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Skip WordPress image policy repair check because response parsing failed. cmsContentId={}",
					contents.getCmsContentId(), e);
			return ImageRepairAction.NONE;
		}
	}

	private boolean isExcludedFeaturedMedia(long featuredMediaId, SiteContents contents, String normalizedBaseUrl) {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(normalizedBaseUrl
					+ "/wp-json/wp/v2/media/" + featuredMediaId + "?_fields=id,source_url", String.class);
			if (!response.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(response.getBody())) {
				return false;
			}
			String sourceUrl = objectMapper.readTree(response.getBody()).path("source_url").asText(null);
			return isExcludedImage(sourceUrl, contents);
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Skip featured media image policy check because media fetch failed. mediaId={} cmsContentId={}",
					featuredMediaId, contents.getCmsContentId(), e);
			return false;
		}
	}

	private UploadedMedia uploadImage(String sourceUrl, String normalizedBaseUrl) {
		return uploadImage(sourceUrl, normalizedBaseUrl, null);
	}

	private UploadedMedia uploadImage(String sourceUrl, String normalizedBaseUrl, Long repairParentPostId) {
		ResponseEntity<byte[]> imageResponse;
		try {
			imageResponse = restTemplate.exchange(sourceUrl, HttpMethod.GET, HttpEntity.EMPTY, byte[].class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("Image download failed. status=" + e.getStatusCode()
					+ " url=" + sourceUrl, e);
		}
		byte[] imageBytes = imageResponse.getBody();
		if (!imageResponse.getStatusCode().is2xxSuccessful() || imageBytes == null || imageBytes.length == 0) {
			throw new IllegalStateException("Image download failed. status=" + imageResponse.getStatusCode()
					+ " url=" + sourceUrl);
		}

		MediaType contentType = resolveImageContentType(imageResponse.getHeaders().getContentType(), sourceUrl);
		if (repairParentPostId != null) {
			UploadedMedia reusableMedia = findReusableRepairMedia(normalizedBaseUrl, repairParentPostId, imageBytes);
			if (reusableMedia != null) {
				log.info("Reuse existing WordPress repair media. cmsContentId={} mediaId={} sourceUrl={}",
						repairParentPostId, reusableMedia.id(), reusableMedia.sourceUrl());
				return reusableMedia;
			}
		}
		HttpHeaders mediaHeaders = new HttpHeaders();
		mediaHeaders.setContentType(contentType);
		mediaHeaders.set(HttpHeaders.AUTHORIZATION, "Basic " + basicAuthToken());
		mediaHeaders.setContentDisposition(ContentDisposition.attachment()
				.filename(resolveFilename(sourceUrl, contentType))
				.build());

		ResponseEntity<String> uploadResponse;
		String mediaEndpoint = normalizedBaseUrl + "/wp-json/wp/v2/media";
		if (repairParentPostId != null) {
			mediaEndpoint += "?post=" + repairParentPostId;
		}
		try {
			uploadResponse = restTemplate.postForEntity(mediaEndpoint,
					new HttpEntity<>(imageBytes, mediaHeaders), String.class);
		} catch (RestClientResponseException e) {
			throw new IllegalStateException("WordPress media upload failed. status=" + e.getStatusCode()
					+ " sourceUrl=" + sourceUrl + " body=" + safeResponseBody(e.getResponseBodyAsString()), e);
		}
		if (!uploadResponse.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("WordPress media upload failed. status=" + uploadResponse.getStatusCode()
					+ " sourceUrl=" + sourceUrl + " body=" + safeResponseBody(uploadResponse.getBody()));
		}
		return parseUploadedMedia(uploadResponse.getBody());
	}

	private UploadedMedia findReusableRepairMedia(String normalizedBaseUrl, Long parentPostId,
			byte[] sourceBytes) {
		try {
			ResponseEntity<String> response = restTemplate.getForEntity(normalizedBaseUrl
					+ "/wp-json/wp/v2/media?parent=" + parentPostId
					+ "&per_page=100&_fields=id,source_url", String.class);
			if (!response.getStatusCode().is2xxSuccessful() || StringUtils.isBlank(response.getBody())) {
				return null;
			}
			JsonNode mediaItems = objectMapper.readTree(response.getBody());
			if (!mediaItems.isArray()) {
				return null;
			}
			for (JsonNode mediaItem : mediaItems) {
				long mediaId = mediaItem.path("id").asLong(0);
				String mediaUrl = mediaItem.path("source_url").asText(null);
				if (mediaId <= 0 || StringUtils.isBlank(mediaUrl)) {
					continue;
				}
				try {
					ResponseEntity<byte[]> mediaResponse = restTemplate.exchange(mediaUrl, HttpMethod.GET,
							HttpEntity.EMPTY, byte[].class);
					if (mediaResponse.getStatusCode().is2xxSuccessful()
							&& Arrays.equals(sourceBytes, mediaResponse.getBody())) {
						return new UploadedMedia(mediaId, mediaUrl);
					}
				} catch (RuntimeException e) {
					RunFatalFailure.propagateIfPresent(e);
					log.warn("Skip unreadable WordPress repair media candidate. cmsContentId={} mediaId={}",
							parentPostId, mediaId, e);
				}
			}
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Skip WordPress repair media reuse lookup. cmsContentId={}", parentPostId, e);
		}
		return null;
	}

	private MediaType resolveImageContentType(MediaType responseContentType, String sourceUrl) {
		if (responseContentType != null && "image".equalsIgnoreCase(responseContentType.getType())) {
			return responseContentType;
		}
		String path = URI.create(sourceUrl).getPath().toLowerCase();
		if (path.endsWith(".png")) {
			return MediaType.IMAGE_PNG;
		}
		if (path.endsWith(".gif")) {
			return MediaType.IMAGE_GIF;
		}
		if (path.endsWith(".webp")) {
			return MediaType.parseMediaType("image/webp");
		}
		return MediaType.IMAGE_JPEG;
	}

	private String resolveFilename(String sourceUrl, MediaType contentType) {
		String path = URI.create(sourceUrl).getPath();
		String filename = path.substring(path.lastIndexOf('/') + 1);
		filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
		filename = filename.replaceAll("[^A-Za-z0-9._-]", "_");
		if (StringUtils.isBlank(filename) || !filename.contains(".")) {
			String extension = contentType.getSubtype().replace("jpeg", "jpg");
			filename = "article-image." + extension;
		}
		return filename;
	}

	private UploadedMedia parseUploadedMedia(String responseBody) {
		if (StringUtils.isBlank(responseBody)) {
			throw new IllegalStateException("WordPress media response is empty.");
		}
		try {
			JsonNode response = objectMapper.readTree(responseBody);
			JsonNode id = response.path("id");
			String sourceUrl = response.path("source_url").asText();
			if (!id.canConvertToLong() || StringUtils.isBlank(sourceUrl)) {
				throw new IllegalStateException("WordPress media response does not contain id and source_url.");
			}
			return new UploadedMedia(id.asLong(), sourceUrl);
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse WordPress media response.", e);
		}
	}

	private String renderPostContent(SiteContents contents, String body) {
		try {
			Template template = templateConfiguration.getTemplate("contents_body.ftl");
			Map<String, Object> variables = new LinkedHashMap<>();
			variables.put("body", firstNotBlank(body, ""));
			variables.put("orgUrl", firstNotBlank(contents.getUrl(), ""));
			SiteInfo siteInfo = contents.getSiteInfo();
			variables.put("orgSite", siteInfo == null ? "" : firstNotBlank(siteInfo.getSiteName(), ""));
			variables.put("orgSiteLogo", siteInfo == null ? "" : firstNotBlank(siteInfo.getLogoUrl(), ""));

			StringWriter writer = new StringWriter();
			template.process(variables, writer);
			return writer.toString();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to render WordPress post content template.", e);
		}
	}

	private String firstNotBlank(String first, String fallback) {
		return StringUtils.isBlank(first) ? fallback : first;
	}

	private List<Integer> resolveCategories(SiteContents contents) {
		SiteCategory category = contents.getSiteCategory();
		if (category == null) {
			return List.of();
		}

		Set<Integer> categories = new LinkedHashSet<>();
		if (category.getCmsCategoryId() != null) {
			categories.add(category.getCmsCategoryId());
		}

		String targetCategory = firstNotBlank(category.getCmsTargetCategory(), category.getTargetCategory());
		if (StringUtils.isBlank(targetCategory)) {
			return new ArrayList<>(categories);
		}
		for (String rawValue : targetCategory.split(",")) {
			String value = rawValue.trim();
			if (StringUtils.isBlank(value)) {
				continue;
			}
			try {
				categories.add(Integer.valueOf(value));
			} catch (NumberFormatException e) {
				log.warn("Skip invalid WordPress category id. siteCategoryId={} targetCategory={}",
						category.getSiteCategoryId(), value);
			}
		}
		return new ArrayList<>(categories);
	}

	private Long parsePostId(String responseBody) {
		if (StringUtils.isBlank(responseBody)) {
			return null;
		}
		try {
			JsonNode id = objectMapper.readTree(responseBody).path("id");
			return id.canConvertToLong() ? id.asLong() : null;
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Failed to parse WordPress post id.", e);
			return null;
		}
	}

	private void validateConfigured() {
		if (StringUtils.isBlank(resolveBaseUrl())) {
			throw new IllegalStateException("web-crawler.wordpress.base-url or web-crawler.host-info is required.");
		}
		if (StringUtils.isBlank(username)) {
			throw new IllegalStateException("web-crawler.wordpress.username is required.");
		}
		if (StringUtils.isBlank(applicationPassword)) {
			throw new IllegalStateException("web-crawler.wordpress.application-password is required.");
		}
	}

	private String resolveBaseUrl() {
		return firstNotBlank(baseUrl, hostInfo);
	}

	private String basicAuthToken() {
		String raw = username + ":" + applicationPassword;
		return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	private String safeResponseBody(String value) {
		if (value == null) {
			return null;
		}
		String masked = value
				.replaceAll("(?i)(authorization\"?\\s*[:=]\\s*\"?)([^\"\\s,}]+)", "$1***")
				.replaceAll("(?i)(application[-_ ]?password\"?\\s*[:=]\\s*\"?)([^\"\\s,}]+)", "$1***");
		if (StringUtils.isNotBlank(applicationPassword)) {
			masked = masked.replace(applicationPassword, "***");
		}
		int maxLength = 2000;
		if (masked.length() <= maxLength) {
			return masked;
		}
		return masked.substring(0, maxLength) + "...(truncated)";
	}

	private String normalizeBaseUrl(String value) {
		String normalized = value.trim();
		if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
			normalized = "https://" + normalized;
		}
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}

	private record UploadedMedia(Long id, String sourceUrl) {
	}

	private record ProcessedPostBody(String html, Long featuredMediaId) {
	}

	private enum ImageRepairAction {
		NONE,
		FEATURED_ONLY,
		FULL
	}
}
