package com.happinesea.webcrawler.service;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.jsoup.nodes.Element;

public final class ArticleImagePolicy {
	private static final Pattern EXCLUDED_TOKEN = Pattern.compile(
			"(^|[/_.-])(logo|icon|avatar|sprite|tracking|pixel|spacer|placeholder|blank|banner|advert|advertisement|ads?)(?=$|[/_.-])");
	private static final Pattern CSS_PIXEL_DIMENSION = Pattern.compile(
			"(?:^|;)\\s*(width|height)\\s*:\\s*(\\d+)px(?:\\s*!important)?\\s*(?:;|$)");

	private ArticleImagePolicy() {
	}

	public static String firstUsableImageUrl(Element image, String configuredLogoUrl) {
		for (String candidate : resolveImageCandidates(image)) {
			if (!isExcludedImage(candidate, configuredLogoUrl, image)) {
				return candidate;
			}
		}
		return null;
	}

	public static List<String> resolveImageCandidates(Element image) {
		if (image == null) {
			return List.of();
		}
		Set<String> candidates = new LinkedHashSet<>();
		for (String attribute : List.of("src", "data-src", "data-original", "data-lazy-src")) {
			addResolvedCandidate(candidates, image, image.attr(attribute));
		}
		addSrcsetCandidates(candidates, image, image.attr("data-srcset"));
		addSrcsetCandidates(candidates, image, image.attr("srcset"));
		Element picture = image.parent();
		if (picture != null && picture.is("picture")) {
			for (Element source : picture.select("source")) {
				addSrcsetCandidates(candidates, source, source.attr("data-srcset"));
				addSrcsetCandidates(candidates, source, source.attr("srcset"));
				addResolvedCandidate(candidates, source, source.attr("src"));
			}
		}
		return new ArrayList<>(candidates);
	}

	public static String resolveUrl(Element context, String rawValue) {
		if (StringUtils.isBlank(rawValue)) {
			return null;
		}
		String value = rawValue.trim();
		try {
			URI candidate = URI.create(value);
			if (candidate.isAbsolute()) {
				return candidate.toString();
			}
			if (context == null || StringUtils.isBlank(context.baseUri())) {
				return null;
			}
			return URI.create(context.baseUri()).resolve(candidate).toString();
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static boolean isRemoteImageUrl(String value) {
		if (StringUtils.isBlank(value)) {
			return false;
		}
		try {
			URI uri = URI.create(value);
			return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
					&& StringUtils.isNotBlank(uri.getHost());
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	public static boolean isExcludedImage(String sourceUrl, String configuredLogoUrl) {
		return isExcludedImage(sourceUrl, configuredLogoUrl, null);
	}

	public static boolean isExcludedImage(String sourceUrl, String configuredLogoUrl, Element image) {
		if (!isRemoteImageUrl(sourceUrl)) {
			return true;
		}
		if (normalizeComparableUrl(sourceUrl).equals(normalizeComparableUrl(configuredLogoUrl))) {
			return true;
		}

		URI uri = URI.create(sourceUrl);
		String path = StringUtils.defaultString(uri.getPath()).toLowerCase(Locale.ROOT);
		String filename = path.substring(path.lastIndexOf('/') + 1);
		if (filename.matches("news_[^/]*\\.png") || path.contains("ogp_default")
				|| EXCLUDED_TOKEN.matcher(path).find()) {
			return true;
		}
		if (image == null) {
			return false;
		}
		if (isTinyDimension(image.attr("width")) || isTinyDimension(image.attr("height"))
				|| hasTinyCssDimension(image.attr("style"))) {
			return true;
		}
		String semantics = String.join(" ", image.id(), image.className(), image.attr("alt"), image.attr("title"),
				image.attr("role"), image.attr("aria-label")).toLowerCase(Locale.ROOT).replace(' ', '-');
		return EXCLUDED_TOKEN.matcher(semantics).find();
	}

	public static String normalizeComparableUrl(String value) {
		if (StringUtils.isBlank(value)) {
			return "";
		}
		int queryIndex = value.indexOf('?');
		int fragmentIndex = value.indexOf('#');
		int end = value.length();
		if (queryIndex >= 0) {
			end = Math.min(end, queryIndex);
		}
		if (fragmentIndex >= 0) {
			end = Math.min(end, fragmentIndex);
		}
		return value.substring(0, end).replaceAll("/+$", "");
	}

	private static void addSrcsetCandidates(Set<String> candidates, Element context, String srcset) {
		if (StringUtils.isBlank(srcset)) {
			return;
		}
		List<SrcsetCandidate> parsedCandidates = new ArrayList<>();
		String[] entries = srcset.split(",");
		for (String entry : entries) {
			String[] parts = entry.trim().split("\\s+");
			if (parts.length > 0) {
				double score = parts.length > 1 ? parseSrcsetDescriptor(parts[1]) : 0;
				parsedCandidates.add(new SrcsetCandidate(parts[0], score));
			}
		}
		parsedCandidates.sort((left, right) -> Double.compare(right.score(), left.score()));
		parsedCandidates.forEach(candidate -> addResolvedCandidate(candidates, context, candidate.url()));
	}

	private static double parseSrcsetDescriptor(String descriptor) {
		try {
			return Double.parseDouble(descriptor.replaceAll("[wx]$", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static void addResolvedCandidate(Set<String> candidates, Element context, String rawValue) {
		String resolved = resolveUrl(context, rawValue);
		if (StringUtils.isNotBlank(resolved)) {
			candidates.add(resolved);
		}
	}

	private static boolean isTinyDimension(String value) {
		if (StringUtils.isBlank(value)) {
			return false;
		}
		try {
			int dimension = Integer.parseInt(value.trim().replaceAll("[^0-9].*$", ""));
			return dimension > 0 && dimension <= 1;
		} catch (NumberFormatException e) {
			return false;
		}
	}

	private static boolean hasTinyCssDimension(String style) {
		if (StringUtils.isBlank(style)) {
			return false;
		}
		Matcher matcher = CSS_PIXEL_DIMENSION.matcher(style.toLowerCase(Locale.ROOT));
		while (matcher.find()) {
			if (Integer.parseInt(matcher.group(2)) <= 1) {
				return true;
			}
		}
		return false;
	}

	private record SrcsetCandidate(String url, double score) {
	}
}
