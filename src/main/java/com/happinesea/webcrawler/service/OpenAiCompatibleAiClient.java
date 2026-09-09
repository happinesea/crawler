package com.happinesea.webcrawler.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OpenAiCompatibleAiClient {
	private static final String DEEPSEEK_MODE = "deepseek";
	private static final String OPENAI_MODE = "openai";

	private final RestTemplateBuilder restTemplateBuilder;
	private final ObjectMapper objectMapper;

	@Value("${web-crawler.ai.api-key:}")
	private String apiKey;
	@Value("${web-crawler.ai.base-url:}")
	private String baseUrl;
	@Value("${web-crawler.ai.model:}")
	private String model;
	@Value("${web-crawler.ai.timeout-ms:30000}")
	private int timeoutMs;
	@Value("${web-crawler.ai.temperature:0.2}")
	private double temperature;
	@Value("${web-crawler.ai.max-output-tokens:2048}")
	private int maxOutputTokens;

	public OpenAiCompatibleAiClient(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.objectMapper = objectMapper;
	}

	public boolean isConfigured(String mode) {
		return StringUtils.isNotBlank(apiKey) && StringUtils.isNotBlank(resolveModel(mode));
	}

	public AiAnalysisResult analyze(String mode, String systemPrompt, String userPrompt) {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.setBearerAuth(apiKey);

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", resolveModel(mode));
		body.put("messages", List.of(
				Map.of("role", "system", "content", systemPrompt),
				Map.of("role", "user", "content", userPrompt)));
		body.put("temperature", temperature);
		body.put("max_tokens", maxOutputTokens);
		body.put("response_format", Map.of("type", "json_object"));
		body.put("stream", false);

		ResponseEntity<String> response = createRestTemplate().postForEntity(resolveEndpoint(mode),
				new HttpEntity<>(body, headers), String.class);

		if (!response.getStatusCode().is2xxSuccessful()) {
			throw new IllegalStateException("AI analysis failed. status=" + response.getStatusCode());
		}
		return parseResult(response.getBody());
	}

	private AiAnalysisResult parseResult(String responseBody) {
		try {
			JsonNode root = objectMapper.readTree(responseBody);
			JsonNode choices = root.path("choices");
			if (!choices.isArray() || choices.isEmpty()) {
				throw new IllegalStateException("AI response does not contain choices.");
			}

			String content = choices.get(0).path("message").path("content").asText("");
			JsonNode result = objectMapper.readTree(stripCodeFence(content));
			return new AiAnalysisResult(result.path("title").asText(null), result.path("content").asText(null));
		} catch (Exception e) {
			throw new IllegalStateException("Failed to parse AI response.", e);
		}
	}

	protected RestTemplate createRestTemplate() {
		return restTemplateBuilder
				.setConnectTimeout(Duration.ofMillis(timeoutMs))
				.setReadTimeout(Duration.ofMillis(timeoutMs))
				.build();
	}

	private String stripCodeFence(String value) {
		String normalized = StringUtils.trimToEmpty(value);
		if (normalized.startsWith("```")) {
			normalized = normalized.replaceFirst("^```(?:json)?\\s*", "");
			normalized = normalized.replaceFirst("\\s*```$", "");
		}
		return normalized;
	}

	private String resolveEndpoint(String mode) {
		return normalizeBaseUrl(resolveBaseUrl(mode)) + "/chat/completions";
	}

	private String resolveBaseUrl(String mode) {
		if (StringUtils.isNotBlank(baseUrl)) {
			return baseUrl;
		}
		if (DEEPSEEK_MODE.equalsIgnoreCase(mode)) {
			return "https://api.deepseek.com";
		}
		if (OPENAI_MODE.equalsIgnoreCase(mode)) {
			return "https://api.openai.com/v1";
		}
		throw new IllegalArgumentException("Unsupported AI mode: " + mode);
	}

	private String resolveModel(String mode) {
		if (StringUtils.isNotBlank(model)) {
			return model;
		}
		if (DEEPSEEK_MODE.equalsIgnoreCase(mode)) {
			return "deepseek-v4-flash";
		}
		return "";
	}

	private String normalizeBaseUrl(String value) {
		String normalized = value.trim();
		while (normalized.endsWith("/")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		return normalized;
	}
}
