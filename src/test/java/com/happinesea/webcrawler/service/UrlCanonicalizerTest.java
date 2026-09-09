package com.happinesea.webcrawler.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class UrlCanonicalizerTest {
	private final UrlCanonicalizer canonicalizer = new UrlCanonicalizer();

	@Test
	void normalizesEquivalentUrlsAndDropsTrackingParameters() {
		String first = canonicalizer.normalize(
				"HTTPS://Example.COM:443/a//b/%7e/?utm_source=x&b=2&a=1#fragment");
		String second = canonicalizer.normalize("https://example.com/a/b/~?a=1&b=2");

		assertThat(first).isEqualTo("https://example.com/a/b/~?a=1&b=2");
		assertThat(first).isEqualTo(second);
		assertThat(canonicalizer.normalize("https://example.com/a%2fb"))
				.isEqualTo("https://example.com/a%2Fb");
	}

	@Test
	void recordsRedirectButRejectsExternalCanonicalByDefault() {
		Document document = Jsoup.parse("""
				<html><head><link rel="canonical" href="https://external.example/story" /></head></html>
				""", "https://news.example/redirected");

		var result = canonicalizer.canonicalize("https://news.example/requested", document);

		assertThat(result.redirectedUrl()).isEqualTo("https://news.example/redirected");
		assertThat(result.canonicalUrl()).isEqualTo("https://external.example/story");
		assertThat(result.normalizedUrl()).isEqualTo("https://news.example/redirected");
		assertThat(result.normalizedUrlHash()).hasSize(64);
	}

	@Test
	void leavesRedirectNullWhenResponseUrlMatchesRequestAndRejectsUserInfo() {
		Document document = Jsoup.parse("<html></html>", "https://news.example/story");

		var result = canonicalizer.canonicalize("https://news.example/story", document);

		assertThat(result.redirectedUrl()).isNull();
		org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
				() -> canonicalizer.normalize("https://user:secret@news.example/story"));
	}
}
