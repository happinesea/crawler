package com.happinesea.webcrawler.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class WebCrawlerStartupValidatorTest {
	@Test
	void cmsDisabledDoesNotRequireWordPressSettings() {
		WebCrawlerStartupValidator validator = new WebCrawlerStartupValidator();
		ReflectionTestUtils.setField(validator, "skipCmsPost", true);
		ReflectionTestUtils.setField(validator, "aiMode", "off");
		ReflectionTestUtils.setField(validator, "wordpressBaseUrl", "");
		ReflectionTestUtils.setField(validator, "hostInfo", "");
		ReflectionTestUtils.setField(validator, "wordpressUsername", "");
		ReflectionTestUtils.setField(validator, "wordpressApplicationPassword", "");

		assertDoesNotThrow(validator::validate);
	}

	@Test
	void aiEnabledRequiresAiSettings() {
		WebCrawlerStartupValidator validator = new WebCrawlerStartupValidator();
		ReflectionTestUtils.setField(validator, "skipCmsPost", true);
		ReflectionTestUtils.setField(validator, "aiMode", "openai");
		ReflectionTestUtils.setField(validator, "aiApiKey", "");
		ReflectionTestUtils.setField(validator, "aiBaseUrl", "");
		ReflectionTestUtils.setField(validator, "aiModel", "");

		assertThrows(IllegalStateException.class, validator::validate);
	}

	@Test
	void cmsEnabledRequiresWordPressSettings() {
		WebCrawlerStartupValidator validator = new WebCrawlerStartupValidator();
		ReflectionTestUtils.setField(validator, "skipCmsPost", false);
		ReflectionTestUtils.setField(validator, "aiMode", "off");
		ReflectionTestUtils.setField(validator, "wordpressBaseUrl", "");
		ReflectionTestUtils.setField(validator, "hostInfo", "");
		ReflectionTestUtils.setField(validator, "wordpressUsername", "");
		ReflectionTestUtils.setField(validator, "wordpressApplicationPassword", "");

		assertThrows(IllegalStateException.class, validator::validate);
	}

	@Test
	void cmsEnabledAcceptsHostInfoWhenWordPressBaseUrlEnvironmentIsEmpty() {
		WebCrawlerStartupValidator validator = new WebCrawlerStartupValidator();
		ReflectionTestUtils.setField(validator, "skipCmsPost", false);
		ReflectionTestUtils.setField(validator, "aiMode", "off");
		ReflectionTestUtils.setField(validator, "wordpressBaseUrl", "");
		ReflectionTestUtils.setField(validator, "hostInfo", "baidu.tokyo");
		ReflectionTestUtils.setField(validator, "wordpressUsername", "publisher");
		ReflectionTestUtils.setField(validator, "wordpressApplicationPassword", "secret");

		assertDoesNotThrow(validator::validate);
	}
}
