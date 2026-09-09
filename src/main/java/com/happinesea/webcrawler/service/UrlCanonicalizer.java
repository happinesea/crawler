package com.happinesea.webcrawler.service;

import java.net.IDN;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.google.common.net.InternetDomainName;

@Component
public class UrlCanonicalizer {
	private static final Set<String> TRACKING_KEYS = Set.of(
			"fbclid", "gclid", "dclid", "msclkid", "yclid", "mc_cid", "mc_eid");
	private static final Pattern PERCENT_ESCAPE = Pattern.compile("%([0-9a-fA-F]{2})");

	@Value("${web-crawler.canonical.allow-same-site:false}")
	private boolean allowSameSite;
	@Value("${web-crawler.canonical.allowed-hosts:}")
	private Set<String> allowedHosts = Set.of();

	public CanonicalUrl canonicalize(String requestedUrl, Document document) {
		String redirectedUrl = blankToNull(document == null ? null : document.location());
		if (redirectedUrl != null && requestedUrl != null && normalize(redirectedUrl).equals(normalize(requestedUrl))) {
			redirectedUrl = null;
		}
		String baseUrl = firstNotBlank(redirectedUrl, requestedUrl);
		String canonicalUrl = extractLinkCanonical(document, baseUrl);
		String ogUrl = extractOgUrl(document, baseUrl);
		String selected = firstNotBlank(validHttpCandidate(redirectedUrl),
				acceptedCandidate(requestedUrl, canonicalUrl), acceptedCandidate(requestedUrl, ogUrl), requestedUrl);
		String normalized = normalize(selected);
		return new CanonicalUrl(requestedUrl, redirectedUrl, canonicalUrl, normalized, sha256(normalized));
	}

	public CanonicalUrl canonicalize(String requestedUrl) {
		String normalized = normalize(requestedUrl);
		return new CanonicalUrl(requestedUrl, null, null, normalized, sha256(normalized));
	}

	public String normalize(String value) {
		if (StringUtils.isBlank(value)) {
			throw new IllegalArgumentException("URL is required");
		}
		try {
			URI source = URI.create(URI.create(value.trim()).toASCIIString()).normalize();
			String scheme = source.getScheme() == null ? null : source.getScheme().toLowerCase(Locale.ROOT);
			String host = source.getHost() == null ? null : IDN.toASCII(source.getHost()).toLowerCase(Locale.ROOT);
			if (scheme == null || host == null || !(scheme.equals("http") || scheme.equals("https"))) {
				throw new IllegalArgumentException("Only absolute HTTP(S) URLs are supported: " + value);
			}
			if (source.getRawUserInfo() != null) {
				throw new IllegalArgumentException("URL userinfo is not allowed");
			}
			int port = source.getPort();
			if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
				port = -1;
			}
			String path = normalizePath(source.getRawPath());
			String query = normalizeQuery(source.getRawQuery());
			String authority = host + (port < 0 ? "" : ":" + port);
			return scheme + "://" + authority + path + (query == null ? "" : "?" + query);
		} catch (RuntimeException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid URL: " + value, e);
		}
	}

	private String extractLinkCanonical(Document document, String baseUrl) {
		if (document == null) {
			return null;
		}
		Element canonical = document.selectFirst("link[rel~=canonical][href]");
		String value = canonical == null ? null : canonical.attr("href");
		if (StringUtils.isBlank(value)) {
			return null;
		}
		return URI.create(baseUrl).resolve(value.trim()).toString();
	}

	private String extractOgUrl(Document document, String baseUrl) {
		if (document == null) {
			return null;
		}
		Element ogUrl = document.selectFirst("meta[property=og:url][content]");
		String value = ogUrl == null ? null : ogUrl.attr("content");
		return StringUtils.isBlank(value) ? null : URI.create(baseUrl).resolve(value.trim()).toString();
	}

	private String validHttpCandidate(String candidate) {
		if (StringUtils.isBlank(candidate)) {
			return null;
		}
		try {
			normalize(candidate);
			return candidate;
		} catch (RuntimeException ignored) {
			return null;
		}
	}

	private String acceptedCandidate(String requested, String candidate) {
		if (StringUtils.isBlank(requested) || StringUtils.isBlank(candidate)) {
			return null;
		}
		try {
			String requestedHost = URI.create(requested).getHost().toLowerCase(Locale.ROOT);
			String candidateHost = URI.create(candidate).getHost().toLowerCase(Locale.ROOT);
			if (sameOrigin(URI.create(requested), URI.create(candidate))
					|| containsIgnoreCase(allowedHosts, candidateHost)) {
				return candidate;
			}
			if (allowSameSite && registrableDomain(requestedHost).equals(registrableDomain(candidateHost))) {
				return candidate;
			}
		} catch (RuntimeException ignored) {
			return null;
		}
		return null;
	}

	private boolean sameOrigin(URI first, URI second) {
		return first.getScheme().equalsIgnoreCase(second.getScheme())
				&& first.getHost().equalsIgnoreCase(second.getHost())
				&& effectivePort(first) == effectivePort(second);
	}

	private int effectivePort(URI uri) {
		if (uri.getPort() >= 0) {
			return uri.getPort();
		}
		return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
	}

	private String registrableDomain(String host) {
		InternetDomainName domain = InternetDomainName.from(host);
		return domain.hasPublicSuffix() ? domain.topPrivateDomain().toString() : host;
	}

	private boolean containsIgnoreCase(Set<String> values, String candidate) {
		return values.stream().anyMatch(value -> value.trim().equalsIgnoreCase(candidate));
	}

	private String normalizePath(String rawPath) {
		String path = StringUtils.isBlank(rawPath) ? "/" : rawPath.replaceAll("/{2,}", "/");
		path = decodeUnreserved(path);
		if (path.length() > 1 && path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		return path;
	}

	private String normalizeQuery(String rawQuery) {
		if (StringUtils.isBlank(rawQuery)) {
			return null;
		}
		List<String> parts = new ArrayList<>();
		for (String part : rawQuery.split("&")) {
			String key = part.contains("=") ? part.substring(0, part.indexOf('=')) : part;
			String normalizedKey = decodeUnreserved(key).toLowerCase(Locale.ROOT);
			if (normalizedKey.startsWith("utm_") || TRACKING_KEYS.contains(normalizedKey)) {
				continue;
			}
			parts.add(decodeUnreserved(part));
		}
		parts.sort(Comparator.naturalOrder());
		return parts.isEmpty() ? null : String.join("&", parts);
	}

	private String decodeUnreserved(String value) {
		Matcher matcher = PERCENT_ESCAPE.matcher(value);
		StringBuffer result = new StringBuffer();
		while (matcher.find()) {
			char decoded = (char) Integer.parseInt(matcher.group(1), 16);
			String replacement = isUnreserved(decoded) ? Character.toString(decoded)
					: "%" + matcher.group(1).toUpperCase(Locale.ROOT);
			matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
		}
		matcher.appendTail(result);
		return result.toString();
	}

	private boolean isUnreserved(char value) {
		return Character.isLetterOrDigit(value) || value == '-' || value == '.' || value == '_' || value == '~';
	}

	private String sha256(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
					.digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (Exception e) {
			throw new IllegalStateException("SHA-256 is unavailable", e);
		}
	}

	private String firstNotBlank(String... values) {
		for (String value : values) {
			if (StringUtils.isNotBlank(value)) {
				return value;
			}
		}
		return null;
	}

	private String blankToNull(String value) {
		return StringUtils.isBlank(value) ? null : value;
	}

	public record CanonicalUrl(String requestedUrl, String redirectedUrl, String canonicalUrl,
			String normalizedUrl, String normalizedUrlHash) {
	}
}
