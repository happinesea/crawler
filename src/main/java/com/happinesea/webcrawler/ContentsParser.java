package com.happinesea.webcrawler;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.Connection.Response;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.service.ArticleImagePolicy;
import com.happinesea.webcrawler.service.UrlCanonicalizer;
import com.happinesea.webcrawler.service.UrlCanonicalizer.CanonicalUrl;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ContentsParser {
	@Value("${web-crawler.external-connect-timeout-ms:10000}")
	private int externalConnectTimeoutMs;
	@Value("${web-crawler.external-connect-retry-count:3}")
	private int externalConnectRetryCount;
	@Value("${web-crawler.wordpress-source.max-pages:20}")
	private int wordpressMaxPages;
	@Autowired
	private UrlCanonicalizer urlCanonicalizer = new UrlCanonicalizer();
	private final ObjectMapper objectMapper = new ObjectMapper();

	public List<SiteContents> loadCategoryContentsList(SiteCategory category) {
		if (category == null) {
			return null;
		}
		try {
			if (isWordPressCategory(category)) {
				return loadWordPressContentsList(category);
			}

			Document doc = getDocumentWithRetry(firstNotBlank(category.getCategoryListUrl(), category.getCategoryUrl()));

			// get contents list
			Elements elements = selectListElements(doc, category.getListRecordSelectId());
			if (elements == null || elements.isEmpty()) {
				return null;
			}

			List<SiteContents> result = new ArrayList<SiteContents>();
			for (Element element : elements) {
				SiteContents contents = new SiteContents();
				contents.setSiteCategory(category);
				contents.setTitle(extractTitle(element, category.getTitleRecordSelectId()));
				if (StringUtils.isBlank(contents.getTitle())) {
					continue;
				}
				String url = extractUrl(element, category.getContentsUrlSelectId());
				if (StringUtils.isBlank(url)) {
					continue;
				}
				url = java.net.URI.create(firstNotBlank(doc.location(),
						firstNotBlank(category.getCategoryListUrl(), category.getCategoryUrl())))
						.resolve(url).toString();
				applyCanonicalUrl(contents, urlCanonicalizer.canonicalize(url));
				contents.setProcessStatus(ProcessStatus.NONE);
				result.add(contents);
			}

			return result;

		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn(String.format("Invalid load category [%s], null).", category.getSiteCategoryId()), e);
			return null;
		}
	}

	public SiteContents loadContents(SiteContents contents) throws NotFoundContentsException {
		if (contents == null || StringUtils.isBlank(contents.getTitle()) || StringUtils.isBlank(contents.getUrl())) {
			throw new IllegalArgumentException(String.format("Invalid load contents is empty."));
		}
		if (contents.getSiteCategory() == null) {
			throw new IllegalArgumentException(
					String.format("Invalid category info for load contents url: %s", contents.getUrl()));
		}

		try {
			if (isWordPressCategory(contents.getSiteCategory()) && StringUtils.isNotBlank(contents.getContents())) {
				return contents;
			}

			String requestedUrl = contents.getUrl();
			Document doc = getDocumentWithRetry(requestedUrl);
			List<Document> imageDocuments = new ArrayList<>();
			imageDocuments.add(doc);
			applyCanonicalUrl(contents, urlCanonicalizer.canonicalize(requestedUrl, doc));
			SiteCategory category = contents.getSiteCategory();
			Elements elements = selectBodyElements(doc, category.getBodySelectId());
			String description = extractDescription(doc);
			Element captureImage = selectCaptureImage(doc, category);
			String moreBodyUrl = findMoreBodyUrl(doc, category.getMoreBodySelectTxt());
			if (StringUtils.isNotBlank(moreBodyUrl) && StringUtils.isNotBlank(category.getMoreBodySelectId())) {
				Document moreDoc = getDocumentWithRetry(moreBodyUrl);
				applyCanonicalUrl(contents, urlCanonicalizer.canonicalize(moreBodyUrl, moreDoc));
				Elements moreElements = selectBodyElements(moreDoc, category.getMoreBodySelectId());
				if (moreElements != null && !moreElements.isEmpty()) {
					elements = moreElements;
					description = firstNotBlank(extractDescription(moreDoc), description);
					imageDocuments.add(0, moreDoc);
				}
				if (captureImage == null) {
					captureImage = selectCaptureImage(moreDoc, category);
				}
			}
			if (elements == null || elements.isEmpty()) {
				throw new NotFoundContentsException(String.format("site contents (%s) is empty.", contents.getUrl()));
			}

			String fallbackDescription = summarizeText(elements.text());
			StringBuilder sb = new StringBuilder(1024);
			if (captureImage != null && !containsImageSource(elements, captureImage.attr("src"))) {
				sb.append(captureImage.outerHtml());
			}
			for (Element element : elements) {
				log.debug(element.html());
				sb.append(element.outerHtml());
			}
			contents.setContents(sb.toString());
			contents.setDescription(firstNotBlank(description, fallbackDescription));
			contents.setFeaturedImageUrl(selectFeaturedImageUrl(imageDocuments, elements, category));
			
			return contents;
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			throw new NotFoundContentsException(String.format("Invalid load contents [%s], null).", contents.getUrl()),
					e);
		}
	}

	private List<SiteContents> loadWordPressContentsList(SiteCategory category) throws Exception {
		String endpoint = resolveWordPressPostsApiUrl(category);
		if (StringUtils.isBlank(endpoint)) {
			return null;
		}

		List<SiteContents> result = new ArrayList<>();
		int maxPages = Math.max(1, wordpressMaxPages);
		int totalPages = maxPages;
		for (int page = 1; page <= Math.min(maxPages, totalPages); page++) {
			String pageEndpoint = page == 1 ? endpoint
					: replaceQueryParameter(endpoint, "page", Integer.toString(page));
			Response response = getResponseWithRetry(pageEndpoint);
			String responseBody = response.body();
			if (StringUtils.isBlank(responseBody)) {
				break;
			}
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode posts = root.isArray() ? root : root.path("posts");
			if (posts == null || !posts.isArray() || posts.isEmpty()) {
				break;
			}
			String totalPagesHeader = response.header("X-WP-TotalPages");
			totalPages = parsePositiveInt(totalPagesHeader, totalPages);
			for (JsonNode post : posts) {
				String title = cleanHtml(renderedText(post.path("title")));
				String url = firstNotBlank(post.path("link").asText(null), renderedText(post.path("guid")));
				String body = renderedText(post.path("content"));
				if (StringUtils.isBlank(title) || StringUtils.isBlank(url) || StringUtils.isBlank(body)) {
					continue;
				}

				SiteContents contents = new SiteContents();
				contents.setSiteCategory(category);
				contents.setTitle(title);
				applyCanonicalUrl(contents, urlCanonicalizer.canonicalize(url));
				contents.setDescription(cleanHtml(renderedText(post.path("excerpt"))));
				contents.setContents(body);
				contents.setFeaturedImageUrl(resolveWordPressFeaturedImageUrl(endpoint, post));
				contents.setProcessStatus(ProcessStatus.NONE);
				result.add(contents);
			}
			if (StringUtils.isBlank(totalPagesHeader) && posts.size() < 20) {
				break;
			}
		}
		return result.isEmpty() ? null : result;
	}

	private String resolveWordPressPostsApiUrl(SiteCategory category) throws Exception {
		String configuredUrl = firstNotBlank(category.getCategoryListUrl(), category.getCategoryUrl());
		if (StringUtils.isBlank(configuredUrl)) {
			return null;
		}
		if (StringUtils.containsIgnoreCase(configuredUrl, "/wp-json/wp/v2/posts")) {
			return appendSourceCategory(category,
					appendQueryIfMissing(appendQueryIfMissing(configuredUrl, "per_page=20"), "_embed=1"));
		}
		String siteUrl = category.getSiteInfo() == null ? null : category.getSiteInfo().getSiteUrl();
		String baseUrl = firstNotBlank(siteUrl, configuredUrl);
		int schemeSeparator = baseUrl.indexOf("://");
		if (schemeSeparator < 0) {
			return null;
		}
		int pathStart = baseUrl.indexOf('/', schemeSeparator + 3);
		String origin = pathStart < 0 ? baseUrl : baseUrl.substring(0, pathStart);
		return appendSourceCategory(category, origin + "/wp-json/wp/v2/posts?per_page=20&_embed=1");
	}

	private String appendSourceCategory(SiteCategory category, String endpoint) throws Exception {
		Integer sourceCategoryId = category.getSourceCategoryId();
		if (sourceCategoryId == null) {
			sourceCategoryId = resolveSourceCategoryIdFromSlug(category, endpoint);
		}
		return sourceCategoryId == null ? endpoint
				: replaceQueryParameter(endpoint, "categories", sourceCategoryId.toString());
	}

	private Integer resolveSourceCategoryIdFromSlug(SiteCategory category, String postsEndpoint) throws Exception {
		String slug = extractWordPressCategorySlug(category.getCategoryUrl());
		if (StringUtils.isBlank(slug)) {
			return null;
		}
		int apiIndex = postsEndpoint.indexOf("/wp-json/wp/v2/");
		if (apiIndex < 0) {
			throw new IllegalStateException("WordPress posts endpoint does not contain the REST API path: "
					+ postsEndpoint);
		}
		String categoriesEndpoint = postsEndpoint.substring(0, apiIndex)
				+ "/wp-json/wp/v2/categories?slug="
				+ URLEncoder.encode(slug, StandardCharsets.UTF_8)
				+ "&per_page=1&_fields=id,slug";
		JsonNode categories = objectMapper.readTree(getBodyWithRetry(categoriesEndpoint));
		if (!categories.isArray() || categories.isEmpty()
				|| !categories.path(0).path("id").canConvertToInt()) {
			throw new IllegalStateException("WordPress source category slug was not resolved: " + slug);
		}
		int resolvedId = categories.path(0).path("id").asInt();
		if (resolvedId <= 0) {
			throw new IllegalStateException("WordPress source category id is invalid for slug: " + slug);
		}
		log.info("Resolved WordPress source category without changing master data. siteCategoryId={} slug={} sourceCategoryId={}",
				category.getSiteCategoryId(), slug, resolvedId);
		return resolvedId;
	}

	private String extractWordPressCategorySlug(String categoryUrl) {
		if (StringUtils.isBlank(categoryUrl)) {
			return null;
		}
		try {
			String path = URI.create(categoryUrl).getPath();
			if (StringUtils.isBlank(path)) {
				return null;
			}
			String marker = "/category/";
			int markerIndex = path.indexOf(marker);
			if (markerIndex < 0) {
				return null;
			}
			String remainder = path.substring(markerIndex + marker.length()).replaceAll("^/+|/+$", "");
			if (StringUtils.isBlank(remainder)) {
				return null;
			}
			String[] segments = remainder.split("/");
			return segments[segments.length - 1];
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("Invalid WordPress category URL: " + categoryUrl, e);
		}
	}

	private String resolveWordPressFeaturedImageUrl(String postsEndpoint, JsonNode post) {
		String embeddedUrl = post.path("_embedded").path("wp:featuredmedia").path(0).path("source_url").asText(null);
		if (StringUtils.isNotBlank(embeddedUrl)) {
			return embeddedUrl;
		}

		JsonNode featuredMedia = post.path("featured_media");
		if (!featuredMedia.canConvertToLong() || featuredMedia.asLong() <= 0) {
			return null;
		}
		String mediaEndpoint = resolveWordPressMediaApiUrl(postsEndpoint, featuredMedia.asLong());
		if (StringUtils.isBlank(mediaEndpoint)) {
			return null;
		}
		try {
			JsonNode media = objectMapper.readTree(getBodyWithRetry(mediaEndpoint));
			String sourceUrl = media.path("source_url").asText(null);
			return StringUtils.isBlank(sourceUrl) ? null : sourceUrl;
		} catch (Exception e) {
			RunFatalFailure.propagateIfPresent(e);
			log.warn("Failed to fetch WordPress featured media. mediaId={} postsEndpoint={}",
					featuredMedia.asLong(), postsEndpoint, e);
			return null;
		}
	}

	private String resolveWordPressMediaApiUrl(String postsEndpoint, long mediaId) {
		if (StringUtils.isBlank(postsEndpoint)) {
			return null;
		}
		int apiIndex = postsEndpoint.indexOf("/wp-json/wp/v2/");
		if (apiIndex < 0) {
			return null;
		}
		return postsEndpoint.substring(0, apiIndex) + "/wp-json/wp/v2/media/" + mediaId;
	}

	private String appendQueryIfMissing(String url, String query) {
		if (StringUtils.contains(url, query)) {
			return url;
		}
		return url + (url.contains("?") ? "&" : "?") + query;
	}

	private String renderedText(JsonNode node) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return null;
		}
		if (node.has("rendered")) {
			return node.path("rendered").asText(null);
		}
		return node.asText(null);
	}

	private String cleanHtml(String html) {
		if (StringUtils.isBlank(html)) {
			return null;
		}
		String text = Jsoup.parseBodyFragment(html).text();
		return StringUtils.isBlank(text) ? null : text;
	}

	private String summarizeText(String text) {
		if (StringUtils.isBlank(text)) {
			return null;
		}
		String normalized = text.replaceAll("\\s+", " ").trim();
		if (normalized.length() <= 240) {
			return normalized;
		}
		return normalized.substring(0, 240);
	}

	private Document getDocumentWithRetry(String url) throws Exception {
		int maxTry = Math.max(1, externalConnectRetryCount);
		Exception last = null;
		for (int attempt = 1; attempt <= maxTry; attempt++) {
			try {
				return Jsoup.connect(url)
						.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
								+ "(KHTML, like Gecko) Chrome/125.0 Safari/537.36")
						.referrer("https://news.yahoo.co.jp/")
						.timeout(externalConnectTimeoutMs)
						.get();
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				last = e;
				log.warn("Failed to fetch url={} attempt={}/{}", url, attempt, maxTry, e);
			}
		}
		throw last;
	}

	private String getBodyWithRetry(String url) throws Exception {
		return getResponseWithRetry(url).body();
	}

	private Response getResponseWithRetry(String url) throws Exception {
		int maxTry = Math.max(1, externalConnectRetryCount);
		Exception last = null;
		for (int attempt = 1; attempt <= maxTry; attempt++) {
			try {
				Response response = Jsoup.connect(url)
						.userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
								+ "(KHTML, like Gecko) Chrome/125.0 Safari/537.36")
						.referrer("https://news.yahoo.co.jp/")
						.timeout(externalConnectTimeoutMs)
						.ignoreContentType(true)
						.execute();
				return response;
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				last = e;
				log.warn("Failed to fetch url={} attempt={}/{}", url, attempt, maxTry, e);
			}
		}
		throw last;
	}

	private String replaceQueryParameter(String url, String name, String value) {
		String pattern = "([?&])" + java.util.regex.Pattern.quote(name) + "=[^&]*";
		if (url.matches(".*" + pattern + ".*")) {
			return url.replaceFirst(pattern, "$1" + name + "=" + value);
		}
		return url + (url.contains("?") ? "&" : "?") + name + "=" + value;
	}

	private int parsePositiveInt(String value, int fallback) {
		try {
			int parsed = Integer.parseInt(value);
			return parsed > 0 ? parsed : fallback;
		} catch (Exception ignored) {
			return fallback;
		}
	}

	private void applyCanonicalUrl(SiteContents contents, CanonicalUrl canonical) {
		contents.setRequestedUrl(canonical.requestedUrl());
		contents.setRedirectedUrl(canonical.redirectedUrl());
		contents.setCanonicalUrl(canonical.canonicalUrl());
		contents.setNormalizedUrl(canonical.normalizedUrl());
		contents.setNormalizedUrlHash(canonical.normalizedUrlHash());
		contents.setSourceUrl(canonical.requestedUrl());
		contents.setUrl(canonical.normalizedUrl());
	}

	private Elements selectListElements(Document doc, String configuredSelector) {
		String[] selectors = {
				configuredSelector,
				"#uamods-topics > ul > li",
				"li[data-ual-view-type=list]",
				".newsFeed_list > li",
				"a[href*=/pickup/]"
		};
		for (String selector : selectors) {
			if (StringUtils.isBlank(selector)) {
				continue;
			}
			Elements elements = doc.select(selector);
			if (elements != null && !elements.isEmpty()) {
				return normalizeListElements(elements);
			}
		}
		return new Elements();
	}

	private String extractTitle(Element element, String configuredSelector) {
		String title = selectText(element, configuredSelector);
		if (isUsableTitle(title)) {
			return title;
		}
		title = selectText(element, "a[href*=/pickup/] > div:last-child > div:first-child");
		if (isUsableTitle(title)) {
			return title;
		}
		if (element.is("a[href*=/pickup/]")) {
			title = selectText(element, ":scope > div:last-child > div:first-child");
			if (isUsableTitle(title)) {
				return title;
			}
		}
		title = selectText(element, "a[href*=/pickup/]");
		if (isUsableTitle(title)) {
			return title;
		}
		title = element.text();
		return isUsableTitle(title) ? title : null;
	}

	private String selectText(Element element, String selector) {
		if (StringUtils.isBlank(selector)) {
			return null;
		}
		Elements selected = element.select(selector);
		if (selected == null || selected.isEmpty()) {
			return null;
		}
		Element first = selected.first();
		return first == null ? null : firstNotBlank(first.text(), first.html());
	}

	private String extractUrl(Element element, String configuredSelector) {
		String url = selectUrl(element, configuredSelector);
		if (StringUtils.isNotBlank(url)) {
			return url;
		}
		url = selectUrl(element, "a[href*=/pickup/]");
		if (StringUtils.isNotBlank(url)) {
			return url;
		}
		if ("a".equalsIgnoreCase(element.tagName())) {
			return firstNotBlank(element.attr("abs:href"), element.attr("href"));
		}
		return null;
	}

	private String selectUrl(Element element, String selector) {
		if (StringUtils.isBlank(selector)) {
			return null;
		}
		Elements selected = element.select(selector);
		if (selected == null || selected.isEmpty()) {
			return null;
		}
		return firstNotBlank(selected.attr("abs:href"), selected.attr("href"));
	}

	private String firstNotBlank(String first, String fallback) {
		return StringUtils.isBlank(first) ? fallback : first;
	}

	private boolean isWordPressCategory(SiteCategory category) {
		return category != null && category.getSiteInfo() != null
				&& ContentsType.Wordpress.equals(category.getSiteInfo().getContentsType());
	}

	private Elements normalizeListElements(Elements elements) {
		Elements normalized = new Elements();
		for (Element element : elements) {
			Elements pickupLinks = element.select("a[href*=/pickup/]");
			if (pickupLinks.size() <= 1) {
				normalized.add(element);
				continue;
			}
			for (Element link : pickupLinks) {
				Element record = link.closest("li[data-ual-view-type=list]");
				if (record == null) {
					record = link.closest("li");
				}
				if (record == null) {
					record = link;
				}
				if (!normalized.contains(record)) {
					normalized.add(record);
				}
			}
		}
		return normalized;
	}

	private boolean isUsableTitle(String title) {
		if (StringUtils.isBlank(title) || title.length() > 200) {
			return false;
		}
		int pickupDateCount = 0;
		var matcher = java.util.regex.Pattern.compile("\\d{1,2}/\\d{1,2}\\([^)]{1,3}\\)").matcher(title);
		while (matcher.find()) {
			pickupDateCount++;
		}
		return pickupDateCount <= 1;
	}

	private Elements selectBodyElements(Document doc, String configuredSelector) {
		String[] selectors = {
				configuredSelector,
				"article p",
				"article",
				"main article p",
				"main p"
		};
		for (String selector : selectors) {
			if (StringUtils.isBlank(selector)) {
				continue;
			}
			Elements elements = doc.select(selector);
			if (elements != null && !elements.isEmpty()) {
				return elements;
			}
		}
		return new Elements();
	}

	private Element selectCaptureImage(Document doc, SiteCategory category) {
		if (doc == null) {
			return null;
		}
		String logoUrl = resolveLogoUrl(category);
		for (Element image : doc.select("article img, main article img")) {
			String imageUrl = ArticleImagePolicy.firstUsableImageUrl(image, logoUrl);
			if (StringUtils.isBlank(imageUrl)) {
				continue;
			}
			Element copy = image.clone();
			copy.attr("src", imageUrl);
			copy.removeAttr("srcset");
			copy.removeAttr("data-srcset");
			copy.removeAttr("data-src");
			copy.removeAttr("data-original");
			copy.removeAttr("data-lazy-src");
			copy.removeAttr("class");
			return copy;
		}
		return null;
	}

	private String selectFeaturedImageUrl(List<Document> documents, Elements bodyElements, SiteCategory category) {
		String logoUrl = resolveLogoUrl(category);
		for (String selector : List.of("link[rel=image_src][href]", "meta[itemprop=image][content]")) {
			String attribute = selector.startsWith("link") ? "href" : "content";
			String candidate = selectMetadataImage(documents, selector, attribute, logoUrl);
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		for (String selector : List.of(
				"meta[property=og:image][content]",
				"meta[property=og:image:url][content]",
				"meta[property=og:image:secure_url][content]",
				"meta[name=twitter:image][content]")) {
			String candidate = selectMetadataImage(documents, selector, "content", logoUrl);
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		for (Document document : documents) {
			String candidate = selectJsonLdImage(document, logoUrl);
			if (StringUtils.isNotBlank(candidate)) {
				return candidate;
			}
		}
		String bodyCandidate = selectFirstBodyImage(bodyElements, logoUrl);
		if (StringUtils.isNotBlank(bodyCandidate)) {
			return bodyCandidate;
		}
		for (Document document : documents) {
			bodyCandidate = selectFirstBodyImage(document.select("article img, main article img"), logoUrl);
			if (StringUtils.isNotBlank(bodyCandidate)) {
				return bodyCandidate;
			}
		}
		return null;
	}

	private String selectMetadataImage(List<Document> documents, String selector, String attribute, String logoUrl) {
		for (Document document : documents) {
			for (Element element : document.select(selector)) {
				String candidate = ArticleImagePolicy.resolveUrl(element, element.attr(attribute));
				if (!ArticleImagePolicy.isExcludedImage(candidate, logoUrl)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private String selectJsonLdImage(Document document, String logoUrl) {
		for (Element script : document.select("script[type=application/ld+json]")) {
			try {
				List<String> candidates = new ArrayList<>();
				collectArticleJsonLdImages(objectMapper.readTree(script.data()), candidates);
				for (String rawCandidate : candidates) {
					String candidate = ArticleImagePolicy.resolveUrl(script, rawCandidate);
					if (!ArticleImagePolicy.isExcludedImage(candidate, logoUrl)) {
						return candidate;
					}
				}
			} catch (Exception e) {
				RunFatalFailure.propagateIfPresent(e);
				log.debug("Skip invalid article JSON-LD while resolving featured image.", e);
			}
		}
		return null;
	}

	private void collectArticleJsonLdImages(JsonNode node, List<String> candidates) {
		if (node == null || node.isMissingNode() || node.isNull()) {
			return;
		}
		if (node.isArray()) {
			node.forEach(child -> collectArticleJsonLdImages(child, candidates));
			return;
		}
		if (!node.isObject()) {
			return;
		}
		if (isArticleJsonLdType(node.path("@type"))) {
			collectJsonLdImageValues(node.path("image"), candidates);
		}
		node.elements().forEachRemaining(child -> collectArticleJsonLdImages(child, candidates));
	}

	private boolean isArticleJsonLdType(JsonNode type) {
		if (type.isArray()) {
			for (JsonNode value : type) {
				if (isArticleJsonLdType(value)) {
					return true;
				}
			}
			return false;
		}
		String value = type.asText("");
		return "Article".equalsIgnoreCase(value) || "NewsArticle".equalsIgnoreCase(value)
				|| "BlogPosting".equalsIgnoreCase(value);
	}

	private void collectJsonLdImageValues(JsonNode image, List<String> candidates) {
		if (image == null || image.isMissingNode() || image.isNull()) {
			return;
		}
		if (image.isTextual()) {
			candidates.add(image.asText());
			return;
		}
		if (image.isArray()) {
			image.forEach(value -> collectJsonLdImageValues(value, candidates));
			return;
		}
		if (image.isObject()) {
			String value = firstNotBlank(image.path("url").asText(null), image.path("contentUrl").asText(null));
			if (StringUtils.isNotBlank(value)) {
				candidates.add(value);
			}
		}
	}

	private String selectFirstBodyImage(Elements elements, String logoUrl) {
		if (elements == null) {
			return null;
		}
		for (Element element : elements) {
			if (element.is("img")) {
				String candidate = ArticleImagePolicy.firstUsableImageUrl(element, logoUrl);
				if (StringUtils.isNotBlank(candidate)) {
					return candidate;
				}
			}
			for (Element image : element.select("img")) {
				String candidate = ArticleImagePolicy.firstUsableImageUrl(image, logoUrl);
				if (StringUtils.isNotBlank(candidate)) {
					return candidate;
				}
			}
		}
		return null;
	}

	private String resolveLogoUrl(SiteCategory category) {
		return category == null || category.getSiteInfo() == null ? null : category.getSiteInfo().getLogoUrl();
	}

	private String extractDescription(Document doc) {
		if (doc == null) {
			return null;
		}
		for (String selector : List.of(
				"meta[name=description]",
				"meta[property=og:description]",
				"meta[name=twitter:description]")) {
			Element meta = doc.selectFirst(selector);
			if (meta == null) {
				continue;
			}
			String value = firstNotBlank(meta.attr("content"), meta.attr("value"));
			if (StringUtils.isNotBlank(value)) {
				return value.trim();
			}
		}
		return null;
	}

	private boolean containsImageSource(Elements elements, String sourceUrl) {
		if (elements == null || StringUtils.isBlank(sourceUrl)) {
			return false;
		}
		String comparableSourceUrl = ArticleImagePolicy.normalizeComparableUrl(sourceUrl);
		for (Element image : elements.select("img")) {
			for (String candidate : ArticleImagePolicy.resolveImageCandidates(image)) {
				if (comparableSourceUrl.equals(ArticleImagePolicy.normalizeComparableUrl(candidate))) {
					return true;
				}
			}
		}
		return false;
	}

	private String findMoreBodyUrl(Document doc, String moreBodySelectTxt) {
		if (doc == null || StringUtils.isBlank(moreBodySelectTxt)) {
			return null;
		}
		for (String rawPattern : moreBodySelectTxt.split(",")) {
			String pattern = rawPattern.trim();
			if (StringUtils.isBlank(pattern)) {
				continue;
			}
			for (Element link : doc.select("a[href]")) {
				if (StringUtils.contains(link.text(), pattern)) {
					return firstNotBlank(link.attr("abs:href"), link.attr("href"));
				}
			}
		}
		return null;
	}
}
