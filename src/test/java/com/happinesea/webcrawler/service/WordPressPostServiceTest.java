package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import com.happinesea.webcrawler.Const.ContentsType;
import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfo;

class WordPressPostServiceTest {
	@Test
	void postReusesExistingWordPressPostBeforeCreatingForNewContent() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo(containsString("/wp-json/wp/v2/posts?search=")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						[{"id":2976,"content":{"raw":"<p><a href='https://source.example/news'>source</a></p>"}}]
						""", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setProcessStatus(ProcessStatus.NONE);
		assertEquals(2976L, service.post(contents));
		server.verify();
	}

	@Test
	void postReconcilesPostCreatedBeforeResponseTimeout() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new SocketTimeoutException("response timed out")));
		server.expect(requestTo(containsString("/wp-json/wp/v2/posts?search=")))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						[{"id":2976,"content":{"raw":"<p><a href='https://source.example/news'>source</a></p>"}}]
						""", MediaType.APPLICATION_JSON));

		assertEquals(2976L, service.post(createContents()));
		server.verify();
	}

	@Test
	void postFailsClosedWhenMultipleWordPressPostsMatchSourceUrl() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo(containsString("/wp-json/wp/v2/posts?search=")))
				.andRespond(withSuccess("""
						[{"id":2976,"content":{"raw":"<a href='https://source.example/news'>one</a>"}},
						 {"id":2977,"content":{"raw":"<a href='https://source.example/news'>two</a>"}}]
						""", MediaType.APPLICATION_JSON));

		assertThrows(IllegalStateException.class, () -> service.post(createContentsWithProcessStatus(ProcessStatus.NONE)));
		server.verify();
	}

	@Test
	void postSendsCreatePostRequestToWordPress() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		String token = Base64.getEncoder().encodeToString("wp-user:app password".getBytes());

		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, "Basic " + token))
				.andExpect(jsonPath("$.title").value("Title"))
				.andExpect(jsonPath("$.content").value(containsString("<div>Description</div>")))
				.andExpect(jsonPath("$.content").value(containsString("href=\"https://source.example/news\"")))
				.andExpect(jsonPath("$.status").value("publish"))
				.andRespond(withSuccess("{\"id\":123}", MediaType.APPLICATION_JSON));

		SiteContents contents = new SiteContents();
		contents.setTitle("Title");
		contents.setDescription("Description");
		contents.setContents("Body");
		contents.setUrl("https://source.example/news");

		Long cmsContentId = service.post(contents);

		assertEquals(123L, cmsContentId);
		server.verify();
	}

	@Test
	void postUsesContentsAndCategoriesWhenContractTypeIsNotNone() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.title").value("Title"))
				.andExpect(jsonPath("$.content").value(containsString("<div>Body</div>")))
				.andExpect(jsonPath("$.categories[0]").value(2))
				.andExpect(jsonPath("$.categories[1]").value(3))
				.andRespond(withSuccess("{\"id\":456}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContractType("1");
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setTargetCategory("2, 3");
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(456L, cmsContentId);
		server.verify();
	}

	@ParameterizedTest
	@CsvSource({
			"0, Description",
			"1, Body",
			"2, Body",
			"9, Body",
			"unknown, Description",
			"'', Description"
	})
	void postUsesFullContentOnlyForExplicitContractAllowlist(String contractType, String expectedBody) {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.content").value(containsString("<div>" + expectedBody + "</div>")))
				.andRespond(withSuccess("{\"id\":470}", MediaType.APPLICATION_JSON));

		assertEquals(470L, service.post(createContentsWithSiteInfo(contractType)));
		server.verify();
	}

	@Test
	void postUpdatesCreatedPostWithSourceMetadataWithoutCapabilityProbe() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andExpect(jsonPath("$.content").value(containsString("<div>Description</div>")))
				.andExpect(jsonPath("$.status").value("draft"))
				.andRespond(withSuccess("{\"id\":471}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/471"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta.source_site_info_id").value(314))
				.andExpect(jsonPath("$.meta.source_contract_type").value("future-contract"))
				.andExpect(jsonPath("$.meta.source_url").value("https://canonical.example/article"))
				.andExpect(jsonPath("$.meta.source_site").value("Source Site"))
				.andExpect(jsonPath("$.status").doesNotExist())
				.andRespond(withSuccess("{\"id\":471}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/471"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andExpect(jsonPath("$.status").value("publish"))
				.andRespond(withSuccess("{\"id\":471}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContentsWithSourceMetadata("future-contract");
		contents.setSourceUrl("https://canonical.example/article");
		assertEquals(471L, service.post(contents));
		server.verify();
	}

	@Test
	void postOmitsAllSourceMetadataWhenSiteInfoIdIsNull() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andRespond(withSuccess("{\"id\":479}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContentsWithSourceMetadata("1");
		contents.getSiteCategory().getSiteInfo().setSiteInfoId(null);
		assertEquals(479L, service.post(contents));
		server.verify();
	}

	@Test
	void postSerializesNullSourceMetadataStringsAsEmptyValues() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andRespond(withSuccess("{\"id\":480}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/480"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta.source_site_info_id").value(314))
				.andExpect(jsonPath("$.meta.source_contract_type").value(""))
				.andExpect(jsonPath("$.meta.source_url").value(""))
				.andExpect(jsonPath("$.meta.source_site").value(""))
				.andExpect(jsonPath("$.status").doesNotExist())
				.andRespond(withSuccess("{\"id\":480}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/480"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("publish"))
				.andRespond(withSuccess("{\"id\":480}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContentsWithSourceMetadata(null);
		contents.getSiteCategory().getSiteInfo().setSiteName(null);
		contents.setSourceUrl(null);
		contents.setUrl(null);
		assertEquals(480L, service.post(contents));
		server.verify();
	}

	@Test
	void metadataRejectionKeepsTheCreatedLegacyPostWithoutRetryingCreate() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andRespond(withSuccess("{\"id\":481}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/481"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":\"rest_invalid_param\"}"));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/481"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("publish"))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andRespond(withSuccess("{\"id\":481}", MediaType.APPLICATION_JSON));

		assertEquals(481L, service.post(createContentsWithSourceMetadata("1")));
		server.verify();
	}

	@Test
	void metadataBadRequestIsNotLegacyFallbackWhenStatusOnlyUpdateAlsoFails() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("draft"))
				.andRespond(withSuccess("{\"id\":485}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/485"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/485"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andRespond(withStatus(HttpStatus.BAD_REQUEST));

		SiteContents contents = createContentsWithSourceMetadata("1");
		assertThrows(IllegalStateException.class, () -> service.post(contents));
		assertEquals(485L, contents.getCmsContentId());
		server.verify();
	}

	@Test
	void metadataServerFailureKeepsTheCreatedPostWithoutRetryingCreate() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("draft"))
				.andRespond(withSuccess("{\"id\":482}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/482"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

		SiteContents contents = createContentsWithSourceMetadata("1");
		assertThrows(IllegalStateException.class, () -> service.post(contents));
		assertEquals(482L, contents.getCmsContentId());
		server.verify();
	}

	@Test
	void metadataNetworkFailureKeepsTheCreatedPostWithoutRetryingCreate() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("draft"))
				.andRespond(withSuccess("{\"id\":483}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/483"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withException(new IOException("connection reset")));

		SiteContents contents = createContentsWithSourceMetadata("1");
		assertThrows(IllegalStateException.class, () -> service.post(contents));
		assertEquals(483L, contents.getCmsContentId());
		server.verify();
	}

	@Test
	void updateSourceMetadataRetriesTheExistingPostId() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/484"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.meta.source_site_info_id").value(314))
				.andExpect(jsonPath("$.status").doesNotExist())
				.andRespond(withSuccess("{\"id\":484}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/484"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.status").value("publish"))
				.andRespond(withSuccess("{\"id\":484}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContentsWithSourceMetadata("1");
		contents.setCmsContentId(484L);
		service.updateSourceMetadata(contents);

		server.verify();
	}

	@Test
	void postUploadsBodyImagesReplacesUrlsAndSetsFirstImageAsFeaturedMedia() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source.example/images/photo.jpg?size=large"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header(HttpHeaders.AUTHORIZATION, containsString("Basic ")))
				.andExpect(header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"photo.jpg\""))
				.andExpect(content().bytes(new byte[] { 1, 2, 3 }))
				.andRespond(withSuccess(
						"{\"id\":88,\"source_url\":\"https://example.com/wp-content/uploads/photo.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(88))
				.andExpect(jsonPath("$.content").value(
						containsString("src=\"https://example.com/wp-content/uploads/photo.jpg\"")))
				.andExpect(jsonPath("$.content").value(
						org.hamcrest.Matchers.not(containsString("data-src="))))
				.andExpect(jsonPath("$.content").value(
						org.hamcrest.Matchers.not(containsString("srcset="))))
				.andRespond(withSuccess("{\"id\":460}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setUrl("https://source.example/news/article");
		contents.setDescription("""
				<p>Article</p>
				<img src="/images/photo.jpg?size=large"
				     srcset="/images/photo-small.jpg 320w"
				     data-src="https://source.example/images/lazy.jpg">
				<img src="/images/photo.jpg?size=large">
				""");

		assertEquals(460L, service.post(contents));
		server.verify();
	}

	@Test
	void postUsesLazyImageUrlWhenSrcIsBlank() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://cdn.example/lazy.png"))
				.andRespond(withSuccess(new byte[] { 4, 5 }, MediaType.APPLICATION_OCTET_STREAM));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andExpect(header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE))
				.andRespond(withSuccess(
						"{\"id\":89,\"source_url\":\"https://example.com/wp-content/uploads/lazy.png\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(89))
				.andRespond(withSuccess("{\"id\":461}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<img src=\"\" data-src=\"https://cdn.example/lazy.png\">");

		assertEquals(461L, service.post(contents));
		server.verify();
	}

	@Test
	void postSkipsImageWhenMediaResponseDoesNotContainSourceUrl() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source.example/photo.jpg"))
				.andRespond(withSuccess(new byte[] { 1 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess("{\"id\":90}", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").doesNotExist())
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("photo.jpg"))))
				.andRespond(withSuccess("{\"id\":462}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<img src=\"https://source.example/photo.jpg\">");

		assertEquals(462L, service.post(contents));
		server.verify();
	}

	@Test
	void postUsesCmsTargetCategoryBeforeLegacyTargetCategory() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.categories[0]").value(10))
				.andExpect(jsonPath("$.categories[1]").value(11))
				.andRespond(withSuccess("{\"id\":457}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		SiteCategory category = new SiteCategory();
		category.setTargetCategory("2, 3");
		category.setCmsTargetCategory("10, 11");
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(457L, cmsContentId);
		server.verify();
	}

	@Test
	void postMergesCmsCategoryIdAndStringCategorySettings() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.categories[0]").value(99))
				.andExpect(jsonPath("$.categories[1]").value(10))
				.andExpect(jsonPath("$.categories[2]").value(11))
				.andRespond(withSuccess("{\"id\":459}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		SiteCategory category = new SiteCategory();
		category.setCmsCategoryId(99);
		category.setCmsTargetCategory("10, 11");
		category.setTargetCategory("2, 3");
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(459L, cmsContentId);
		server.verify();
	}

	@Test
	void postDeduplicatesMergedCategoriesAndSkipsInvalidValues() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.categories[0]").value(99))
				.andExpect(jsonPath("$.categories[1]").value(10))
				.andExpect(jsonPath("$.categories.length()").value(2))
				.andRespond(withSuccess("{\"id\":462}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		SiteCategory category = new SiteCategory();
		category.setCmsCategoryId(99);
		category.setCmsTargetCategory("99, invalid, 10, 10");
		category.setTargetCategory("2, 3");
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(462L, cmsContentId);
		server.verify();
	}

	@Test
	void updateCategoriesSendsMergedCategoriesToExistingPost() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/777"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.categories[0]").value(99))
				.andExpect(jsonPath("$.categories[1]").value(10))
				.andExpect(jsonPath("$.categories[2]").value(11))
				.andRespond(withSuccess("{\"id\":777}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setCmsContentId(777L);
		SiteCategory category = new SiteCategory();
		category.setCmsCategoryId(99);
		category.setCmsTargetCategory("10, 11");
		category.setTargetCategory("2, 3");
		contents.setSiteCategory(category);

		service.updateCategories(contents);

		server.verify();
	}

	@Test
	void updateCategoriesRequiresCmsContentId() {
		WordPressPostService service = configuredService();

		assertThrows(IllegalArgumentException.class, () -> service.updateCategories(createContents()));
	}

	@Test
	void updateCategoriesSkipsWhenNoCategoriesAreConfigured() {
		WordPressPostService service = configuredService();
		SiteContents contents = createContents();
		contents.setCmsContentId(777L);
		contents.setSiteCategory(new SiteCategory());

		service.updateCategories(contents);
	}

	@Test
	void repairImagePolicyUpdatesExistingPostWhenSourceLogoRemainsInContent() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/777?_fields=id,content,featured_media"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{
						  "id": 777,
						  "featured_media": 0,
						  "content": {
						    "rendered": "<p><img src=\\"https://s.yimg.jp/c/logo/f/2.0/news_r_34_2x.png\\"></p><div>old body</div>"
						  }
						}
						""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/777"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.featured_media").value(0))
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("news_r_34_2x.png"))))
				.andExpect(jsonPath("$.content").value(containsString("<div>Description</div>")))
				.andRespond(withSuccess("{\"id\":777}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setCmsContentId(777L);
		contents.setSiteCategory(new SiteCategory());

		assertEquals(true, service.repairImagePolicyIfNeeded(contents));
		server.verify();
	}

	@Test
	void postReusesFeaturedUploadWhenTheSameImageIsInTheBody() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source.example/images/shared.jpg?exp=10800"))
				.andRespond(withSuccess(new byte[] { 1, 2 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":91,\"source_url\":\"https://example.com/wp-content/uploads/shared.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(91))
				.andExpect(jsonPath("$.content").value(containsString(
						"https://example.com/wp-content/uploads/shared.jpg")))
				.andRespond(withSuccess("{\"id\":463}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setFeaturedImageUrl("https://source.example/images/shared.jpg?exp=10800");
		contents.setDescription(
				"<p>Body</p><img src=\"https://source.example/images/shared.jpg?pri=l&amp;w=640\">");

		assertEquals(463L, service.post(contents));
		server.verify();
	}

	@Test
	void postContinuesWithoutFeaturedMediaWhenConfiguredImageUploadFails() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source.example/images/missing.jpg"))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
						.withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").doesNotExist())
				.andRespond(withSuccess("{\"id\":464}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setFeaturedImageUrl("https://source.example/images/missing.jpg");

		assertEquals(464L, service.post(contents));
		server.verify();
	}

	@Test
	void postPropagatesWrappedInterruptFromFeaturedImageUpload() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		RuntimeException interrupted = new RuntimeException("wrapped", new InterruptedException("stop"));

		server.expect(requestTo("https://source.example/images/interrupted.jpg"))
				.andRespond(request -> {
					throw interrupted;
				});

		SiteContents contents = createContents();
		contents.setFeaturedImageUrl("https://source.example/images/interrupted.jpg");
		try {
			assertEquals(interrupted, assertThrows(RuntimeException.class, () -> service.post(contents)));
			assertEquals(true, Thread.currentThread().isInterrupted());
		} finally {
			Thread.interrupted();
		}
	}

	@Test
	void repairImagePolicySkipsExistingPostWhenNoForbiddenImagesRemain() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/778?_fields=id,content,featured_media"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess("""
						{
						  "id": 778,
						  "featured_media": 0,
						  "content": {
						    "rendered": "<div>clean body</div>"
						  }
						}
						""", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setCmsContentId(778L);
		contents.setSiteCategory(new SiteCategory());

		assertEquals(false, service.repairImagePolicyIfNeeded(contents));
		server.verify();
	}

	@Test
	void repairUploadsSelectedFeaturedImageAndUpdatesExistingPostWithoutRepostingContent() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/2643?_fields=id,content,featured_media"))
				.andRespond(withSuccess("""
						{"id":2643,"featured_media":0,"content":{"rendered":"<div>clean body</div>"}}
						""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://source.example/images/featured.jpg"))
				.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media?parent=2643&per_page=100&_fields=id,source_url"))
				.andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media?post=2643"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().bytes(new byte[] { 1, 2, 3 }))
				.andRespond(withSuccess(
						"{\"id\":901,\"source_url\":\"https://example.com/uploads/featured.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/2643"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.featured_media").value(901))
				.andExpect(jsonPath("$.content").doesNotExist())
				.andExpect(jsonPath("$.categories").doesNotExist())
				.andRespond(withSuccess("{\"id\":2643}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setCmsContentId(2643L);
		contents.setFeaturedImageUrl("https://source.example/images/featured.jpg");
		SiteCategory category = new SiteCategory();
		category.setCmsTargetCategory("8");
		contents.setSiteCategory(category);

		assertEquals(true, service.repairImagePolicyIfNeeded(contents));
		server.verify();
	}

	@Test
	void repairReusesMatchingParentMediaAfterPartialSuccess() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/2643?_fields=id,content,featured_media"))
				.andRespond(withSuccess("""
						{"id":2643,"featured_media":0,"content":{"rendered":"<div>clean body</div>"}}
						""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://source.example/images/featured.jpg"))
				.andRespond(withSuccess(new byte[] { 4, 5, 6 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media?parent=2643&per_page=100&_fields=id,source_url"))
				.andRespond(withSuccess("""
						[{"id":902,"source_url":"https://example.com/uploads/featured.jpg"}]
						""", MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/uploads/featured.jpg"))
				.andRespond(withSuccess(new byte[] { 4, 5, 6 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts/2643"))
				.andExpect(jsonPath("$.featured_media").value(902))
				.andRespond(withSuccess("{\"id\":2643}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setCmsContentId(2643L);
		contents.setFeaturedImageUrl("https://source.example/images/featured.jpg");
		contents.setSiteCategory(new SiteCategory());

		assertEquals(true, service.repairImagePolicyIfNeeded(contents));
		server.verify();
	}

	@Test
	void postFallsBackToLegacyTargetCategoryWhenCmsTargetCategoryIsBlank() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.categories[0]").value(2))
				.andExpect(jsonPath("$.categories[1]").value(3))
				.andRespond(withSuccess("{\"id\":458}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		SiteCategory category = new SiteCategory();
		category.setTargetCategory("2, 3");
		category.setCmsTargetCategory(" ");
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(458L, cmsContentId);
		server.verify();
	}

	@Test
	void postRendersContentTemplateWithSourceMetadataWithoutSourceLogoImage() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").doesNotExist())
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("logo.png"))))
				.andExpect(jsonPath("$.content").value(containsString("<strong>Lead</strong>")))
				.andExpect(jsonPath("$.content").value(containsString("Source &amp; Site: <a target=\"_blank\" rel=\"noopener noreferrer\" href=\"https://source.example/news?id=1&amp;ref=top\"")))
				.andRespond(withSuccess("{\"id\":789}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<strong>Lead</strong>");
		contents.setUrl("https://source.example/news?id=1&ref=top");
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setSiteName("Source & Site");
		siteInfo.setLogoUrl("https://source.example/logo.png?a=1&b=2");
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		contents.setSiteCategory(category);

		Long cmsContentId = service.post(contents);

		assertEquals(789L, cmsContentId);
		server.verify();
	}

	@Test
	void postDoesNotUploadTemplateLogoAndUsesFirstValidBodyImageAsFeaturedMedia() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source.example/article.jpg"))
				.andRespond(withSuccess(new byte[] { 7 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":94,\"source_url\":\"https://example.com/wp-content/uploads/article.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(94))
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("logo.png"))))
				.andExpect(jsonPath("$.content").value(containsString("src=\"https://example.com/wp-content/uploads/article.jpg\"")))
				.andRespond(withSuccess("{\"id\":792}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<p>Lead</p><img src=\"https://source.example/article.jpg\">");
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setSiteName("Source Site");
		siteInfo.setLogoUrl("https://source.example/logo.png");
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		contents.setSiteCategory(category);

		assertEquals(792L, service.post(contents));
		server.verify();
	}

	@Test
	void postSkipsYahooNewsLogoAndUsesNextArticleImageAsFeaturedMedia() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://news.yahoo.co.jp/images/article.jpg"))
				.andRespond(withSuccess(new byte[] { 8 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":95,\"source_url\":\"https://example.com/wp-content/uploads/article.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(95))
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("news_123.png"))))
				.andExpect(jsonPath("$.content").value(containsString("https://example.com/wp-content/uploads/article.jpg")))
				.andRespond(withSuccess("{\"id\":793}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("""
				<img src="https://news.yahoo.co.jp/common/news_123.png">
				<img src="https://news.yahoo.co.jp/images/article.jpg">
				""");

		assertEquals(793L, service.post(contents));
		server.verify();
	}

	@Test
	void postDoesNotSetFeaturedMediaWhenOnlyExcludedImagesExist() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").doesNotExist())
				.andRespond(withSuccess("{\"id\":794}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<img src=\"https://source.example/logo.png\">");
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setLogoUrl("https://source.example/logo.png");
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		contents.setSiteCategory(category);

		assertEquals(794L, service.post(contents));
		server.verify();
	}

	@Test
	void postUsesSourceWordPressFeaturedImageBeforeBodyImages() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source-wp.example/uploads/featured.jpg"))
				.andRespond(withSuccess(new byte[] { 1 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":96,\"source_url\":\"https://example.com/wp-content/uploads/featured.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://source-wp.example/uploads/body.jpg"))
				.andRespond(withSuccess(new byte[] { 2 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":97,\"source_url\":\"https://example.com/wp-content/uploads/body.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(96))
				.andExpect(jsonPath("$.content").value(containsString("https://example.com/wp-content/uploads/body.jpg")))
				.andRespond(withSuccess("{\"id\":795}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setFeaturedImageUrl("https://source-wp.example/uploads/featured.jpg");
		contents.setDescription("<img src=\"https://source-wp.example/uploads/body.jpg\">");
		contents.setSiteCategory(wordPressCategory());

		assertEquals(795L, service.post(contents));
		server.verify();
	}

	@Test
	void postDoesNotUseBodyImageAsFeaturedMediaWhenSourceWordPressHasNoFeaturedImage() {
		WordPressPostService service = configuredService();
		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();

		server.expect(requestTo("https://source-wp.example/uploads/body.jpg"))
				.andRespond(withSuccess(new byte[] { 2 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":98,\"source_url\":\"https://example.com/wp-content/uploads/body.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").doesNotExist())
				.andExpect(jsonPath("$.content").value(containsString("https://example.com/wp-content/uploads/body.jpg")))
				.andRespond(withSuccess("{\"id\":796}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setDescription("<img src=\"https://source-wp.example/uploads/body.jpg\">");
		contents.setSiteCategory(wordPressCategory());

		assertEquals(796L, service.post(contents));
		server.verify();
	}

	@Test
	void postSkipsExpiredImagesAndContinuesWithRemainingImages() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://source.example/expired.jpg"))
				.andRespond(org.springframework.test.web.client.response.MockRestResponseCreators
						.withStatus(HttpStatus.NOT_FOUND));
		server.expect(requestTo("https://source.example/ok.jpg"))
				.andRespond(withSuccess(new byte[] { 1, 2 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":92,\"source_url\":\"https://example.com/wp-content/uploads/ok.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(92))
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("expired.jpg"))))
				.andExpect(jsonPath("$.content").value(containsString("https://example.com/wp-content/uploads/ok.jpg")))
				.andRespond(withSuccess("{\"id\":790}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setUrl("https://source.example/news/article");
		contents.setDescription("""
				<p>Article</p>
				<img src="https://source.example/expired.jpg">
				<img src="https://source.example/ok.jpg">
				""");

		assertEquals(790L, service.post(contents));
		server.verify();
	}

	@Test
	void postSkipsImagesAfterConfiguredUploadLimit() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "maxImageUploadCount", 1);

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://source.example/first.jpg"))
				.andRespond(withSuccess(new byte[] { 1 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andRespond(withSuccess(
						"{\"id\":93,\"source_url\":\"https://example.com/wp-content/uploads/first.jpg\"}",
						MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andExpect(jsonPath("$.featured_media").value(93))
				.andExpect(jsonPath("$.content").value(containsString("https://example.com/wp-content/uploads/first.jpg")))
				.andExpect(jsonPath("$.content").value(org.hamcrest.Matchers.not(containsString("second.jpg"))))
				.andRespond(withSuccess("{\"id\":791}", MediaType.APPLICATION_JSON));

		SiteContents contents = createContents();
		contents.setUrl("https://source.example/news/article");
		contents.setDescription("""
				<img src="https://source.example/first.jpg">
				<img src="https://source.example/second.jpg">
				""");

		assertEquals(791L, service.post(contents));
		server.verify();
	}

	@Test
	void postAddsHttpsAndRemovesTrailingSlashesFromBaseUrl() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "baseUrl", "example.com///");

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

		service.post(createContents());

		server.verify();
	}

	@Test
	void postFallsBackToHostInfoWhenBaseUrlIsBlank() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "baseUrl", "");
		ReflectionTestUtils.setField(service, "hostInfo", "baidu.tokyo");

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://baidu.tokyo/wp-json/wp/v2/posts"))
				.andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

		service.post(createContents());

		server.verify();
	}

	@Test
	void postKeepsHttpBaseUrlScheme() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "baseUrl", "http://example.com/");

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("http://example.com/wp-json/wp/v2/posts"))
				.andRespond(withSuccess("{\"id\":1}", MediaType.APPLICATION_JSON));

		service.post(createContents());

		server.verify();
	}

	@Test
	void postThrowsWhenContentsIsNull() {
		WordPressPostService service = configuredService();

		assertThrows(IllegalArgumentException.class, () -> service.post(null));
	}

	@Test
	void postThrowsWhenBaseUrlIsBlank() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "baseUrl", " ");
		ReflectionTestUtils.setField(service, "hostInfo", " ");

		assertThrows(IllegalStateException.class, () -> service.post(createContents()));
	}

	@Test
	void postThrowsWhenUsernameIsBlank() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "username", "");

		assertThrows(IllegalStateException.class, () -> service.post(createContents()));
	}

	@Test
	void postThrowsWhenApplicationPasswordIsBlank() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "applicationPassword", "");

		assertThrows(IllegalStateException.class, () -> service.post(createContents()));
	}

	@Test
	void postThrowsWhenWordPressReturnsNonSuccessStatus() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "restTemplate", new RestTemplate() {
			@Override
			public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
					Object... uriVariables) {
				return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
			}
		});

		assertThrows(IllegalStateException.class, () -> service.post(createContents()));
	}

	@Test
	void postThrowsWhenWordPressResponseDoesNotContainId() {
		WordPressPostService service = configuredService();

		RestTemplate restTemplate = (RestTemplate) ReflectionTestUtils.getField(service, "restTemplate");
		MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
		server.expect(requestTo("https://example.com/wp-json/wp/v2/posts"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThrows(IllegalStateException.class, () -> service.post(createContents()));
		server.verify();
	}

	@Test
	void postIncludesWordPressErrorBodyWhenRestTemplateThrows() {
		WordPressPostService service = configuredService();
		ReflectionTestUtils.setField(service, "restTemplate", new RestTemplate() {
			@Override
			public <T> ResponseEntity<T> postForEntity(String url, Object request, Class<T> responseType,
					Object... uriVariables) {
				throw HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY,
						"{\"code\":\"rest_cannot_create\"}".getBytes(), null);
			}
		});

		IllegalStateException exception = assertThrows(IllegalStateException.class, () -> service.post(createContents()));

		assertEquals(true, exception.getMessage().contains("401 UNAUTHORIZED"));
		assertEquals(true, exception.getMessage().contains("rest_cannot_create"));
	}

	private WordPressPostService configuredService() {
		WordPressPostService service = new WordPressPostService(new RestTemplateBuilder(), 10000);
		ReflectionTestUtils.setField(service, "baseUrl", "https://example.com/");
		ReflectionTestUtils.setField(service, "hostInfo", "");
		ReflectionTestUtils.setField(service, "username", "wp-user");
		ReflectionTestUtils.setField(service, "applicationPassword", "app password");
		ReflectionTestUtils.setField(service, "postStatus", "publish");
		return service;
	}

	private SiteContents createContentsWithSiteInfo(String contractType) {
		SiteContents contents = createContents();
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setSiteName("Source Site");
		siteInfo.setContractType(contractType);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		contents.setSiteCategory(category);
		return contents;
	}

	private SiteContents createContentsWithSourceMetadata(String contractType) {
		SiteContents contents = createContentsWithSiteInfo(contractType);
		contents.getSiteCategory().getSiteInfo().setSiteInfoId(314);
		return contents;
	}

	private SiteContents createContentsWithImageAndCategory() {
		SiteContents contents = createContentsWithSiteInfo("1");
		contents.setContents("<p>Body</p><img src=\"https://source.example/photo.jpg\">");
		contents.getSiteCategory().setCmsCategoryId(12);
		return contents;
	}

	private void expectArticleImageUpload(MockRestServiceServer server) {
		server.expect(requestTo("https://source.example/photo.jpg"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(new byte[] { 1, 2, 3 }, MediaType.IMAGE_JPEG));
		server.expect(requestTo("https://example.com/wp-json/wp/v2/media"))
				.andExpect(method(HttpMethod.POST))
				.andRespond(withSuccess(
						"{\"id\":88,\"source_url\":\"https://example.com/wp-content/uploads/photo.jpg\"}",
						MediaType.APPLICATION_JSON));
	}

	private SiteContents createContents() {
		SiteContents contents = new SiteContents();
		contents.setTitle("Title");
		contents.setDescription("Description");
		contents.setContents("Body");
		contents.setUrl("https://source.example/news");
		return contents;
	}

	private SiteContents createContentsWithProcessStatus(ProcessStatus status) {
		SiteContents contents = createContents();
		contents.setProcessStatus(status);
		return contents;
	}

	private SiteCategory wordPressCategory() {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		return category;
	}
}
