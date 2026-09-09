package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

class OpenAiCompatibleAiClientTest {

	@Test
	void analyzeSendsOpenAiCompatibleChatCompletionRequestAndParsesResult() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(new RestTemplateBuilder(), new ObjectMapper()) {
			@Override
			protected RestTemplate createRestTemplate() {
				return restTemplate;
			}
		};
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.example.com/v1/");
		ReflectionTestUtils.setField(client, "model", "test-model");
		ReflectionTestUtils.setField(client, "timeoutMs", 30000);
		ReflectionTestUtils.setField(client, "temperature", 0.2);
		ReflectionTestUtils.setField(client, "maxOutputTokens", 2048);

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.example.com/v1/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
				.andExpect(jsonPath("$.model").value("test-model"))
				.andExpect(jsonPath("$.messages[0].role").value("system"))
				.andExpect(jsonPath("$.messages[1].role").value("user"))
				.andExpect(jsonPath("$.response_format.type").value("json_object"))
				.andRespond(withSuccess("""
						{
						  "choices": [
						    {
						      "message": {
						        "content": "{\\"title\\":\\"AI title\\",\\"content\\":\\"AI content\\"}"
						      }
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResult result = client.analyze("openai", "system", "user");

		assertEquals("AI title", result.title());
		assertEquals("AI content", result.contents());
		server.verify();
	}

	@Test
	void isConfiguredUsesDeepSeekDefaultModel() {
		OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(new RestTemplateBuilder(), new ObjectMapper());
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "model", "");

		assertTrue(client.isConfigured("deepseek"));
	}

	@Test
	void isConfiguredReturnsFalseWhenApiKeyOrResolvedModelIsMissing() {
		OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(new RestTemplateBuilder(), new ObjectMapper());
		ReflectionTestUtils.setField(client, "apiKey", "");
		ReflectionTestUtils.setField(client, "model", "gpt-test");

		assertFalse(client.isConfigured("openai"));

		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "model", "");

		assertFalse(client.isConfigured("openai"));
	}

	@Test
	void analyzeUsesDeepSeekDefaultEndpointAndStripsJsonCodeFence() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "");
		ReflectionTestUtils.setField(client, "model", "");
		ReflectionTestUtils.setField(client, "temperature", 0.1);
		ReflectionTestUtils.setField(client, "maxOutputTokens", 512);

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.deepseek.com/chat/completions"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
				.andRespond(withSuccess("""
						{
						  "choices": [
						    {
						      "message": {
						        "content": "```json\\n{\\"title\\":\\"DeepSeek title\\",\\"content\\":\\"DeepSeek content\\"}\\n```"
						      }
						    }
						  ]
						}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResult result = client.analyze("deepseek", "system", "user");

		assertEquals("DeepSeek title", result.title());
		assertEquals("DeepSeek content", result.contents());
		server.verify();
	}

	@Test
	void analyzeUsesOpenAiDefaultEndpointWhenBaseUrlIsBlank() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "");
		ReflectionTestUtils.setField(client, "model", "gpt-test");

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.openai.com/v1/chat/completions"))
				.andRespond(withSuccess("""
						{"choices":[{"message":{"content":"{\\"title\\":\\"OpenAI title\\",\\"content\\":\\"OpenAI content\\"}"}}]}
						""", MediaType.APPLICATION_JSON));

		AiAnalysisResult result = client.analyze("openai", "system", "user");

		assertEquals("OpenAI title", result.title());
		assertEquals("OpenAI content", result.contents());
		server.verify();
	}

	@Test
	void analyzeThrowsWhenModeIsUnsupportedAndBaseUrlIsBlank() {
		OpenAiCompatibleAiClient client = createClient(new RestTemplate());
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "");
		ReflectionTestUtils.setField(client, "model", "");

		assertThrows(IllegalArgumentException.class, () -> client.analyze("other", "system", "user"));
	}

	@Test
	void analyzeThrowsWhenHttpStatusIsNotSuccessful() {
		RestTemplate restTemplate = new RestTemplate() {
			@Override
			public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
					Object... uriVariables) {
				return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
			}
		};
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.example.com");
		ReflectionTestUtils.setField(client, "model", "test-model");

		assertThrows(IllegalStateException.class, () -> client.analyze("openai", "system", "user"));
	}

	@Test
	void analyzeThrowsWhenResponseCannotBeParsed() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.example.com");
		ReflectionTestUtils.setField(client, "model", "test-model");

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.example.com/chat/completions"))
				.andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));

		assertThrows(IllegalStateException.class, () -> client.analyze("openai", "system", "user"));
		server.verify();
	}

	@Test
	void analyzeThrowsWhenChoicesIsNotAnArray() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.example.com");
		ReflectionTestUtils.setField(client, "model", "test-model");

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.example.com/chat/completions"))
				.andRespond(withSuccess("{\"choices\":{}}", MediaType.APPLICATION_JSON));

		assertThrows(IllegalStateException.class, () -> client.analyze("openai", "system", "user"));
		server.verify();
	}

	@Test
	void analyzeThrowsWhenMessageContentIsNotJson() {
		RestTemplate restTemplate = new RestTemplate();
		OpenAiCompatibleAiClient client = createClient(restTemplate);
		ReflectionTestUtils.setField(client, "apiKey", "test-key");
		ReflectionTestUtils.setField(client, "baseUrl", "https://api.example.com");
		ReflectionTestUtils.setField(client, "model", "test-model");

		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(once(), requestTo("https://api.example.com/chat/completions"))
				.andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"not json\"}}]}",
						MediaType.APPLICATION_JSON));

		assertThrows(IllegalStateException.class, () -> client.analyze("openai", "system", "user"));
		server.verify();
	}

	@Test
	void createRestTemplateAppliesConfiguredTimeouts() {
		OpenAiCompatibleAiClient client = new OpenAiCompatibleAiClient(new RestTemplateBuilder(), new ObjectMapper());
		ReflectionTestUtils.setField(client, "timeoutMs", 1000);

		assertNotNull(client.createRestTemplate());
	}

	private OpenAiCompatibleAiClient createClient(RestTemplate restTemplate) {
		return new OpenAiCompatibleAiClient(new RestTemplateBuilder(), new ObjectMapper()) {
			@Override
			protected RestTemplate createRestTemplate() {
				return restTemplate;
			}
		};
	}
}
