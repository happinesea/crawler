package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.happinesea.webcrawler.entity.SiteContents;

@ExtendWith(MockitoExtension.class)
class AiAnalysisServiceTest {

	@Mock
	private OpenAiCompatibleAiClient aiClient;

	@Test
	void prepareForPostWhenModeOffReturnsOriginalContent() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "off");

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertSame(contents, result);
		verify(aiClient, never()).analyze(anyString(), anyString(), anyString());
	}

	@Test
	void prepareForPostWhenContentsIsNullReturnsNull() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "deepseek");

		SiteContents result = service.prepareForPost(null);

		assertSame(null, result);
		verifyNoInteractions(aiClient);
	}

	@Test
	void prepareForPostWhenModeIsBlankReturnsOriginalContent() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", " ");

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertSame(contents, result);
		verifyNoInteractions(aiClient);
	}

	@Test
	void prepareForPostWhenModeIsUnsupportedReturnsOriginalContent() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "anthropic");

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertSame(contents, result);
		verifyNoInteractions(aiClient);
	}

	@Test
	void prepareForPostWhenClientIsNotConfiguredReturnsOriginalContent() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "deepseek");
		when(aiClient.isConfigured("deepseek")).thenReturn(false);

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertSame(contents, result);
		verify(aiClient, never()).analyze(anyString(), anyString(), anyString());
	}

	@Test
	void prepareForPostWhenAiSucceedsReturnsAnalyzedCopy() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "deepseek");
		ReflectionTestUtils.setField(service, "maxInputChars", 12000);
		ReflectionTestUtils.setField(service, "systemPrompt",
				"Return only valid JSON with keys title and content.");
		when(aiClient.isConfigured("deepseek")).thenReturn(true);
		when(aiClient.analyze(anyString(), anyString(), anyString()))
				.thenReturn(new AiAnalysisResult("AI title", "AI content"));

		SiteContents contents = createContents();
		contents.setFeaturedImageUrl("https://cdn.example/featured.jpg");

		SiteContents result = service.prepareForPost(contents);

		assertEquals(contents.getSiteContentsId(), result.getSiteContentsId());
		assertEquals(contents.getUrl(), result.getUrl());
		assertEquals(contents.getFeaturedImageUrl(), result.getFeaturedImageUrl());
		assertEquals("AI title", result.getTitle());
		assertEquals("AI content", result.getContents());
	}

	@Test
	void prepareForPostTruncatesLongContentBeforeSendingToAi() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "deepseek");
		ReflectionTestUtils.setField(service, "maxInputChars", 4);
		ReflectionTestUtils.setField(service, "systemPrompt", "system");
		when(aiClient.isConfigured("deepseek")).thenReturn(true);
		when(aiClient.analyze(anyString(), anyString(), anyString()))
				.thenReturn(new AiAnalysisResult("AI title", "AI content"));

		SiteContents contents = createContents();
		contents.setContents("1234567890");

		service.prepareForPost(contents);

		ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
		verify(aiClient).analyze(anyString(), anyString(), prompt.capture());
		assertEquals(true, prompt.getValue().contains("1234"));
		assertEquals(false, prompt.getValue().contains("12345"));
	}

	@Test
	void prepareForPostWhenAiResultIsBlankFallsBackToOriginalFields() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "openai");
		ReflectionTestUtils.setField(service, "maxInputChars", 12000);
		ReflectionTestUtils.setField(service, "systemPrompt", "system");
		when(aiClient.isConfigured("openai")).thenReturn(true);
		when(aiClient.analyze(anyString(), anyString(), anyString())).thenReturn(new AiAnalysisResult("", " "));

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertEquals("Original title", result.getTitle());
		assertEquals("Original content", result.getContents());
	}

	@Test
	void prepareForPostWhenAiFailsReturnsOriginalContent() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "openai");
		ReflectionTestUtils.setField(service, "maxInputChars", 12000);
		ReflectionTestUtils.setField(service, "systemPrompt",
				"Return only valid JSON with keys title and content.");
		when(aiClient.isConfigured("openai")).thenReturn(true);
		when(aiClient.analyze(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));

		SiteContents contents = createContents();

		SiteContents result = service.prepareForPost(contents);

		assertSame(contents, result);
	}

	@Test
	void prepareForPostPropagatesWrappedInterruptInsteadOfFallingBack() {
		AiAnalysisService service = new AiAnalysisService(aiClient);
		ReflectionTestUtils.setField(service, "mode", "openai");
		ReflectionTestUtils.setField(service, "maxInputChars", 12000);
		ReflectionTestUtils.setField(service, "systemPrompt", "system");
		when(aiClient.isConfigured("openai")).thenReturn(true);
		RuntimeException interrupted = new RuntimeException("wrapped", new InterruptedException("stop"));
		when(aiClient.analyze(anyString(), anyString(), anyString())).thenThrow(interrupted);

		try {
			assertSame(interrupted, assertThrows(RuntimeException.class,
					() -> service.prepareForPost(createContents())));
			assertTrue(Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	private SiteContents createContents() {
		SiteContents contents = new SiteContents();
		contents.setSiteContentsId(10L);
		contents.setUrl("https://example.com/news");
		contents.setTitle("Original title");
		contents.setContents("Original content");
		return contents;
	}
}
