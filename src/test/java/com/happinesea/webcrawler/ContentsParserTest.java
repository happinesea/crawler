package com.happinesea.webcrawler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jsoup.Connection;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfo;
import com.happinesea.webcrawler.Const.ContentsType;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

@ExtendWith(OutputCaptureExtension.class)
public class ContentsParserTest {

	private ContentsParser parser;

	private SiteCategory mockCategory;

	@BeforeEach
	void setUp() {
		parser = new ContentsParser();

		mockCategory = new SiteCategory();
		mockCategory.setCategoryUrl("http://test.com");
		mockCategory.setListRecordSelectId("#uamods-topics > ul > li");
		mockCategory.setTitleRecordSelectId(
				"li[data-ual-view-type=\"list\"] a div:nth-of-type(2) div:not(:has(*)):not([class^=\"yads\"])");
		mockCategory.setContentsUrlSelectId("li[data-ual-view-type=\"list\"] > a");
		mockCategory.setBodySelectId("#uamods > div.article_body");
	}

	@AfterEach
	void clearInterruptedStatus() {
		Thread.interrupted();
	}

	@Test
	void categoryListPropagatesSpringDataFailure() throws Exception {
		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenThrow(new DataAccessResourceFailureException("database unavailable"));

			assertThrows(DataAccessResourceFailureException.class,
					() -> parser.loadCategoryContentsList(mockCategory));
		}
	}

	@Test
	void detailLoadPropagatesWrappedInterruptAndRestoresInterruptStatus() throws Exception {
		SiteContents contents = new SiteContents();
		contents.setTitle("title");
		contents.setUrl("http://test.com/article-interrupted");
		contents.setSiteCategory(mockCategory);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect(contents.getUrl())).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenThrow(new RuntimeException("wrapped", new InterruptedException("stop")));

			assertThrows(RuntimeException.class, () -> parser.loadContents(contents));
			assertTrue(Thread.currentThread().isInterrupted());
		}
	}

	@Test
	void testLoadCategoryContentsList_successful() throws Exception {
		// sample URL:https://news.yahoo.co.jp/topics/domestic
		String html = java.nio.file.Files.readString(
				new File("src/main/resources/test-contents/test-category-yahoo.html").toPath(), StandardCharsets.UTF_8);
		Document mockDoc = Jsoup.parse(html);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			// Act
			List<SiteContents> result = parser.loadCategoryContentsList(mockCategory);

			// Assert
			assertThat(result).isNotEmpty();
			assertEquals(25, result.size());

			assertThat(result.get(0).getTitle()).isNotBlank();
			assertThat(result.get(1).getTitle()).isNotBlank();
			assertThat(result.get(2).getTitle()).isNotBlank();
			assertThat(result.get(23).getTitle()).isNotBlank();
			assertThat(result.get(24).getTitle()).isNotBlank();

			assertEquals("https://news.yahoo.co.jp/pickup/6546562", result.get(0).getUrl());
			assertEquals("https://news.yahoo.co.jp/pickup/6546550", result.get(1).getUrl());
			assertEquals("https://news.yahoo.co.jp/pickup/6546543", result.get(2).getUrl());
			assertEquals("https://news.yahoo.co.jp/pickup/6546460", result.get(23).getUrl());
			assertEquals("https://news.yahoo.co.jp/pickup/6546453", result.get(24).getUrl());
		}
	}

	@Test
	void testLoadCategoryContentsList_whenJsoupThrows_logsWarning() throws Exception {
		// Arrange
		SiteCategory mockCategory = new SiteCategory();
		mockCategory.setCategoryUrl("http://fail.com");
		mockCategory.setListRecordSelectId(".item");
		mockCategory.setTitleRecordSelectId(".title");
		mockCategory.setContentsUrlSelectId(".link");
		mockCategory.setSiteCategoryId(123);

		// set up log capture
		Logger logger = (Logger) LoggerFactory.getLogger(ContentsParser.class);
		ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
		listAppender.start();
		logger.addAppender(listAppender);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection mockConn = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://fail.com")).thenReturn(mockConn);
			mockConnectionChain(mockConn);
			when(mockConn.get()).thenThrow(new RuntimeException("connection failed"));

			// Act
			List<SiteContents> result = parser.loadCategoryContentsList(mockCategory);

			// Assert
			assertThat(result).isNull();

			// Log check
			List<ILoggingEvent> logs = listAppender.list;
			assertThat(logs.stream().anyMatch(e -> e.getFormattedMessage().contains("Invalid load category [123]")))
					.isTrue();
		}
	}

	@Test
	void testLoadCategoryContentsList_whenNullCategory_returnsNull() {
		// Act
		List<SiteContents> result = parser.loadCategoryContentsList(null);

		// Assert
		assertThat(result).isNull();
	}

	@Test
	void testLoadContents_successful() throws Exception {
		// Arrange
		String html = java.nio.file.Files.readString(
				new File("src/main/resources/test-contents/test-article-yahoo.html").toPath(), StandardCharsets.UTF_8);
		Document mockDoc = Jsoup.parse(html);

		SiteContents mockContents = new SiteContents();
		mockContents.setUrl("http://test.com/content");
		mockContents.setTitle("Dummy title");
		mockContents.setSiteCategory(mockCategory);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com/content")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			// Act
			SiteContents result = parser.loadContents(mockContents);

			// Assert
			assertThat(result).isNotNull();
			assertThat(result.getTitle()).isEqualTo("Dummy title");
			assertThat(result.getContents()).isNotBlank();
			assertThat(result.getDescription()).isNotBlank();
			assertThat(result.getDescription()).isNotEqualTo(result.getContents());
			assertThat(result.getDescription()).contains("人事院が2025年度の国家公務員");
		}
	}

	@Test
	void testLoadContents_usesMoreBodyPageWhenMoreBodyTextLinkExists() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(".lead");
		category.setMoreBodySelectId(".full-body");
		category.setMoreBodySelectTxt("記事全文を読む,続きを読む");

		SiteContents contents = new SiteContents();
		contents.setUrl("http://test.com/article");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);

		Document firstDoc = Jsoup.parse("""
				<div class="lead">lead only</div>
				<a href="http://test.com/article/full">記事全文を読む</a>
				""");
		Document moreDoc = Jsoup.parse("""
				<div class="full-body">full body text</div>
				""");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection firstConnection = mock(Connection.class);
			Connection moreConnection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com/article")).thenReturn(firstConnection);
			jsoupMock.when(() -> Jsoup.connect("http://test.com/article/full")).thenReturn(moreConnection);
			mockConnectionChain(firstConnection);
			mockConnectionChain(moreConnection);
			when(firstConnection.get()).thenReturn(firstDoc);
			when(moreConnection.get()).thenReturn(moreDoc);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents()).contains("full body text");
			assertThat(result.getContents()).doesNotContain("lead only");
			assertThat(result.getDescription()).isEqualTo("full body text");
			assertThat(result.getUrl()).isEqualTo("http://test.com/article/full");
		}
	}

	@Test
	void testLoadContents_usesBodyTextAsDescriptionWhenMetaDescriptionIsMissing() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(".article-body p");
		SiteContents contents = new SiteContents();
		contents.setUrl("https://news.example/article");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);
		Document document = Jsoup.parse("""
				<div class="article-body">
				  <p>First body paragraph.</p>
				  <p>Second body paragraph.</p>
				</div>
				""", "https://news.example/article");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/article")).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenReturn(document);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents()).contains("First body paragraph.");
			assertThat(result.getDescription()).isEqualTo("First body paragraph. Second body paragraph.");
			assertThat(result.getDescription()).isNotEqualTo(result.getContents());
		}
	}

	@Test
	void testLoadContents_keepsPickupCaptureImageWhenUsingMoreBodyPage() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(".lead");
		category.setMoreBodySelectId(".full-body");
		category.setMoreBodySelectTxt("記事全文を読む");

		SiteContents contents = new SiteContents();
		contents.setUrl("https://news.example/pickup/1");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);

		Document firstDoc = Jsoup.parse("""
				<article>
				  <div><picture><img src="/images/capture.jpg" class="dynamic"></picture></div>
				  <div class="lead">lead only</div>
				  <a href="/articles/1">記事全文を読む</a>
				</article>
				""", "https://news.example/pickup/1");
		Document moreDoc = Jsoup.parse("""
				<div class="full-body"><p>full body text</p></div>
				""", "https://news.example/articles/1");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection firstConnection = mock(Connection.class);
			Connection moreConnection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/pickup/1")).thenReturn(firstConnection);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/articles/1")).thenReturn(moreConnection);
			mockConnectionChain(firstConnection);
			mockConnectionChain(moreConnection);
			when(firstConnection.get()).thenReturn(firstDoc);
			when(moreConnection.get()).thenReturn(moreDoc);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents())
					.contains("<img src=\"https://news.example/images/capture.jpg\">")
					.contains("<p>full body text</p>")
					.doesNotContain("lead only")
					.doesNotContain("class=\"dynamic\"");
			assertThat(result.getUrl()).isEqualTo("https://news.example/articles/1");
		}
	}

	@Test
	void testLoadContents_preservesSelectedBodyElementMarkup() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(".article_body > p");
		SiteContents contents = new SiteContents();
		contents.setUrl("https://news.example/article");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);
		Document document = Jsoup.parse("""
				<div class="article_body">
				  <p>first paragraph</p>
				  <p>second paragraph</p>
				</div>
				""", "https://news.example/article");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/article")).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenReturn(document);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents())
					.contains("<p>first paragraph</p>")
					.contains("<p>second paragraph</p>");
		}
	}

	@Test
	void testLoadContents_setsRelativeBodyImageAsFeaturedImage() throws Exception {
		SiteContents result = loadNormalHtml("""
				<article><div class="body">
				  <p>Body</p><img src="/images/article.jpg">
				</div></article>
				""", "https://news.example/articles/1", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://news.example/images/article.jpg");
		assertThat(result.getContents()).contains("/images/article.jpg");
	}

	@Test
	void testLoadContents_usesLazyImageWhenSrcIsNotUsable() throws Exception {
		SiteContents result = loadNormalHtml("""
				<article><div class="body">
				  <p>Body</p><img src="data:image/gif;base64,AAAA" data-src="//cdn.example/lazy.jpg">
				</div></article>
				""", "https://news.example/articles/2", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://cdn.example/lazy.jpg");
	}

	@Test
	void testLoadContents_usesLargestSrcsetCandidateFromPicture() throws Exception {
		SiteContents result = loadNormalHtml("""
				<article><div class="body"><picture>
				  <source srcset="/images/source-small.webp 320w, /images/source-large.webp 1280w">
				  <img srcset="/images/small.jpg 320w, /images/large.jpg 1280w">
				</picture><p>Body</p></div></article>
				""", "https://news.example/articles/3", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://news.example/images/large.jpg");
	}

	@Test
	void testLoadContents_skipsTrackingAndTinyImagesBeforeValidBodyImage() throws Exception {
		SiteContents result = loadNormalHtml("""
				<article><div class="body">
				  <img src="/images/tracking.gif">
				  <img src="/images/tiny.jpg" width="1" height="1">
				  <img src="/images/article-photo.jpg">
				  <p>Body</p>
				</div></article>
				""", "https://news.example/articles/4", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://news.example/images/article-photo.jpg");
	}

	@Test
	void testLoadContents_succeedsWithoutFeaturedImage() throws Exception {
		SiteContents result = loadNormalHtml(
				"<article><div class=\"body\"><p>Text only</p></div></article>",
				"https://news.example/articles/5", ".body");

		assertThat(result.getFeaturedImageUrl()).isNull();
		assertThat(result.getContents()).contains("Text only");
	}

	@Test
	void testLoadContents_prefersExplicitFeaturedMetadataOverOgAndBodyImages() throws Exception {
		SiteContents result = loadNormalHtml("""
				<head>
				  <link rel="image_src" href="/images/explicit.jpg">
				  <meta property="og:image" content="/images/og.jpg">
				</head>
				<body><article><div class="body"><img src="/images/body.jpg"><p>Body</p></div></article></body>
				""", "https://news.example/articles/6", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://news.example/images/explicit.jpg");
	}

	@Test
	void testLoadContents_usesArticleJsonLdImageBeforeBodyImage() throws Exception {
		SiteContents result = loadNormalHtml("""
				<head><script type="application/ld+json">
				  {"@type":"NewsArticle","image":{"url":"/images/json-ld.jpg"}}
				</script></head>
				<body><article><div class="body"><img src="/images/body.jpg"><p>Body</p></div></article></body>
				""", "https://news.example/articles/7", ".body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo("https://news.example/images/json-ld.jpg");
	}

	@Test
	void testLoadContents_extractsYahooFeaturedImageFromRegressionFixture() throws Exception {
		String html = java.nio.file.Files.readString(
				java.nio.file.Path.of("src/test/resources/fixtures/yahoo-featured-image-article.html"),
				StandardCharsets.UTF_8);

		SiteContents result = loadNormalHtml(html,
				"https://news.yahoo.co.jp/articles/8b02d7c20867d346330a8d545311d4b8cd8c0648",
				".article_body");

		assertThat(result.getFeaturedImageUrl()).isEqualTo(
				"https://newsatcl-pctr.c.yimg.jp/t/amd-img/20260812-00384479-otonans-000-14-view.jpg?exp=10800");
		assertThat(result.getContents()).contains("00384479-otonans-000-14-view.jpg");
	}

	@Test
	void testLoadContents_usesMoreBodyPageDescriptionWhenAvailable() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(".lead");
		category.setMoreBodySelectId(".full-body");
		category.setMoreBodySelectTxt("read more");

		SiteContents contents = new SiteContents();
		contents.setUrl("https://news.example/pickup/1");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);

		Document firstDoc = Jsoup.parse("""
				<meta name="description" content="pickup description">
				<div class="lead">lead only</div>
				<a href="/articles/1">read more</a>
				""", "https://news.example/pickup/1");
		Document moreDoc = Jsoup.parse("""
				<meta name="description" content="article description">
				<div class="full-body"><p>full body text</p></div>
				""", "https://news.example/articles/1");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection firstConnection = mock(Connection.class);
			Connection moreConnection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/pickup/1")).thenReturn(firstConnection);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/articles/1")).thenReturn(moreConnection);
			mockConnectionChain(firstConnection);
			mockConnectionChain(moreConnection);
			when(firstConnection.get()).thenReturn(firstDoc);
			when(moreConnection.get()).thenReturn(moreDoc);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents()).contains("full body text");
			assertThat(result.getDescription()).isEqualTo("article description");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_readsPublicRestPosts() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryListUrl("https://wp.example/wp-json/wp/v2/posts?categories=7");
		String json = """
				[
				  {
				    "link": "https://wp.example/post-1",
				    "title": {"rendered": "WP <b>Title</b>"},
				    "excerpt": {"rendered": "<p>WP excerpt</p>"},
				    "content": {"rendered": "<p>WP body</p>"}
				  }
				]
				""";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			Response response = mock(Response.class);
			jsoupMock.when(() -> Jsoup.connect("https://wp.example/wp-json/wp/v2/posts?categories=7&per_page=20&_embed=1"))
					.thenReturn(connection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(connection);
			when(connection.ignoreContentType(true)).thenReturn(connection);
			when(connection.execute()).thenReturn(response);
			when(response.body()).thenReturn(json);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getUrl()).isEqualTo("https://wp.example/post-1");
			assertThat(result.get(0).getTitle()).isEqualTo("WP Title");
			assertThat(result.get(0).getDescription()).isEqualTo("WP excerpt");
			assertThat(result.get(0).getContents()).isEqualTo("<p>WP body</p>");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_resolvesCategorySlugWithoutChangingMaster() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		siteInfo.setSiteUrl("https://wp.example/");
		SiteCategory category = new SiteCategory();
		category.setSiteCategoryId(9);
		category.setSiteInfo(siteInfo);
		category.setCategoryUrl("https://wp.example/category/news");
		String postsJson = "[{\"link\":\"https://wp.example/news/one\","
				+ "\"title\":{\"rendered\":\"News\"},"
				+ "\"content\":{\"rendered\":\"<p>body</p>\"}}]";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection categoriesConnection = mock(Connection.class);
			Connection postsConnection = mock(Connection.class);
			Response categoriesResponse = mock(Response.class);
			Response postsResponse = mock(Response.class);
			String categoriesEndpoint = "https://wp.example/wp-json/wp/v2/categories"
					+ "?slug=news&per_page=1&_fields=id,slug";
			String postsEndpoint = "https://wp.example/wp-json/wp/v2/posts"
					+ "?per_page=20&_embed=1&categories=19";
			jsoupMock.when(() -> Jsoup.connect(categoriesEndpoint)).thenReturn(categoriesConnection);
			jsoupMock.when(() -> Jsoup.connect(postsEndpoint)).thenReturn(postsConnection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(categoriesConnection);
			mockConnectionChain(postsConnection);
			when(categoriesConnection.ignoreContentType(true)).thenReturn(categoriesConnection);
			when(postsConnection.ignoreContentType(true)).thenReturn(postsConnection);
			when(categoriesConnection.execute()).thenReturn(categoriesResponse);
			when(postsConnection.execute()).thenReturn(postsResponse);
			when(categoriesResponse.body()).thenReturn("[{\"id\":19,\"slug\":\"news\"}]");
			when(postsResponse.body()).thenReturn(postsJson);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getUrl()).isEqualTo("https://wp.example/news/one");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_followsTotalPagesAndSourceCategory() throws Exception {
		ReflectionTestUtils.setField(parser, "wordpressMaxPages", 5);
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryListUrl("https://wp.example/wp-json/wp/v2/posts");
		category.setSourceCategoryId(7);
		String firstJson = "[{\"link\":\"https://wp.example/one\",\"title\":{\"rendered\":\"One\"},"
				+ "\"content\":{\"rendered\":\"<p>one</p>\"}}]";
		String secondJson = "[{\"link\":\"https://wp.example/two\",\"title\":{\"rendered\":\"Two\"},"
				+ "\"content\":{\"rendered\":\"<p>two</p>\"}}]";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection firstConnection = mock(Connection.class);
			Connection secondConnection = mock(Connection.class);
			Response firstResponse = mock(Response.class);
			Response secondResponse = mock(Response.class);
			String base = "https://wp.example/wp-json/wp/v2/posts?per_page=20&_embed=1&categories=7";
			jsoupMock.when(() -> Jsoup.connect(base)).thenReturn(firstConnection);
			jsoupMock.when(() -> Jsoup.connect(base + "&page=2")).thenReturn(secondConnection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(firstConnection);
			mockConnectionChain(secondConnection);
			when(firstConnection.ignoreContentType(true)).thenReturn(firstConnection);
			when(secondConnection.ignoreContentType(true)).thenReturn(secondConnection);
			when(firstConnection.execute()).thenReturn(firstResponse);
			when(secondConnection.execute()).thenReturn(secondResponse);
			when(firstResponse.body()).thenReturn(firstJson);
			when(secondResponse.body()).thenReturn(secondJson);
			when(firstResponse.header("X-WP-TotalPages")).thenReturn("2");
			when(secondResponse.header("X-WP-TotalPages")).thenReturn("2");

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).extracting(SiteContents::getTitle).containsExactly("One", "Two");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_savesEmbeddedFeaturedImageUrl() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryListUrl("https://wp.example/wp-json/wp/v2/posts");
		String json = """
				[
				  {
				    "link": "https://wp.example/post-1",
				    "title": {"rendered": "WP Title"},
				    "excerpt": {"rendered": "<p>WP excerpt</p>"},
				    "content": {"rendered": "<p>WP body</p>"},
				    "featured_media": 10,
				    "_embedded": {
				      "wp:featuredmedia": [
				        {"source_url": "https://wp.example/uploads/featured.jpg"}
				      ]
				    }
				  }
				]
				""";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			Response response = mock(Response.class);
			jsoupMock.when(() -> Jsoup.connect("https://wp.example/wp-json/wp/v2/posts?per_page=20&_embed=1"))
					.thenReturn(connection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(connection);
			when(connection.ignoreContentType(true)).thenReturn(connection);
			when(connection.execute()).thenReturn(response);
			when(response.body()).thenReturn(json);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getFeaturedImageUrl()).isEqualTo("https://wp.example/uploads/featured.jpg");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_fetchesFeaturedImageUrlByMediaId() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryListUrl("https://wp.example/wp-json/wp/v2/posts?categories=7");
		String postsJson = """
				[
				  {
				    "link": "https://wp.example/post-1",
				    "title": {"rendered": "WP Title"},
				    "excerpt": {"rendered": "<p>WP excerpt</p>"},
				    "content": {"rendered": "<p>WP body</p>"},
				    "featured_media": 22
				  }
				]
				""";
		String mediaJson = "{\"id\":22,\"source_url\":\"https://wp.example/uploads/media-featured.jpg\"}";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection postsConnection = mock(Connection.class);
			Connection mediaConnection = mock(Connection.class);
			Response postsResponse = mock(Response.class);
			Response mediaResponse = mock(Response.class);
			jsoupMock.when(() -> Jsoup.connect("https://wp.example/wp-json/wp/v2/posts?categories=7&per_page=20&_embed=1"))
					.thenReturn(postsConnection);
			jsoupMock.when(() -> Jsoup.connect("https://wp.example/wp-json/wp/v2/media/22"))
					.thenReturn(mediaConnection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(postsConnection);
			mockConnectionChain(mediaConnection);
			when(postsConnection.ignoreContentType(true)).thenReturn(postsConnection);
			when(mediaConnection.ignoreContentType(true)).thenReturn(mediaConnection);
			when(postsConnection.execute()).thenReturn(postsResponse);
			when(mediaConnection.execute()).thenReturn(mediaResponse);
			when(postsResponse.body()).thenReturn(postsJson);
			when(mediaResponse.body()).thenReturn(mediaJson);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getFeaturedImageUrl()).isEqualTo("https://wp.example/uploads/media-featured.jpg");
		}
	}

	@Test
	void testLoadWordPressCategoryContentsList_keepsFeaturedImageUrlNullWhenUnavailable() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryListUrl("https://wp.example/wp-json/wp/v2/posts");
		String json = """
				[
				  {
				    "link": "https://wp.example/post-1",
				    "title": {"rendered": "WP Title"},
				    "excerpt": {"rendered": "<p>WP excerpt</p>"},
				    "content": {"rendered": "<p>WP body</p>"},
				    "featured_media": 0
				  }
				]
				""";

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			Response response = mock(Response.class);
			jsoupMock.when(() -> Jsoup.connect("https://wp.example/wp-json/wp/v2/posts?per_page=20&_embed=1"))
					.thenReturn(connection);
			jsoupMock.when(() -> Jsoup.parseBodyFragment(anyString())).thenCallRealMethod();
			mockConnectionChain(connection);
			when(connection.ignoreContentType(true)).thenReturn(connection);
			when(connection.execute()).thenReturn(response);
			when(response.body()).thenReturn(json);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertThat(result.get(0).getFeaturedImageUrl()).isNull();
		}
	}

	@Test
	void testLoadContents_skipsDetailFetchForWordPressContentAlreadyLoaded() throws Exception {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.Wordpress);
		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		SiteContents contents = new SiteContents();
		contents.setUrl("https://wp.example/post-1");
		contents.setTitle("WP title");
		contents.setContents("<p>WP body</p>");
		contents.setSiteCategory(category);

		SiteContents result = parser.loadContents(contents);

		assertThat(result).isSameAs(contents);
		assertThat(result.getContents()).contains("WP body");
	}

	@Test
	void testLoadContents_doesNotDuplicateCaptureImageAlreadyInBody() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId("article");
		SiteContents contents = new SiteContents();
		contents.setUrl("https://news.example/article");
		contents.setTitle("Dummy title");
		contents.setSiteCategory(category);
		Document document = Jsoup.parse("""
				<article>
				  <div><picture><img src="/images/capture.jpg"></picture></div>
				  <p>body</p>
				</article>
				""", "https://news.example/article");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("https://news.example/article")).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenReturn(document);

			SiteContents result = parser.loadContents(contents);

			assertThat(result.getContents().split("capture.jpg", -1)).hasSize(2);
		}
	}

	@Test
	void testLoadContents_whenJsoupFails_logsWarning() throws Exception {

		// Arrange
		String url = "http://example.com";
		SiteCategory category = new SiteCategory();
		category.setBodySelectId("div.content");

		SiteContents inputContents = new SiteContents();
		inputContents.setUrl(url);
		inputContents.setTitle("Test Title");
		inputContents.setSiteCategory(category);

		// Jsoup邵ｺ譬涌Exception郢ｧ蛛ｵ縺帷ｹ晢ｽｭ郢晢ｽｼ邵ｺ蜷ｶ・狗ｹｧ蛹ｻ竕ｧ郢晢ｽ｢郢昴・縺・
		Connection connectionMock = mock(Connection.class);
		mockConnectionChain(connectionMock);
		when(connectionMock.get()).thenThrow(new IOException("Connection failed"));

		// 郢晢ｽ｢郢昴・縺醍ｸｺ・ｮ陝ｾ・ｮ邵ｺ闍難ｽｾ・ｼ邵ｺ・ｿ繝ｻ逎ｯ謦暮ｧ繝ｻﾎ鍋ｹｧ・ｽ郢昴・繝ｩ郢ｧ蛛ｵﾎ皮ｹ昴・縺醍ｸｺ蜉ｱ窶ｻ邵ｺ繝ｻ・玖恆閧ｴ鄂ｲ繝ｻ繝ｻ
		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connectionMock);

			// Act & Assert
			NotFoundContentsException ex = assertThrows(NotFoundContentsException.class, () -> {
				parser.loadContents(inputContents);
			});

			assertThat(ex.getMessage()).contains("Invalid load contents");
			assertThat(ex.getCause()).isInstanceOf(IOException.class);
		}
	}

	@Test
	void testLoadContents_whenNullInput_returnsNull() {
		assertThrows(IllegalArgumentException.class, () -> parser.loadContents(null));
	}

	@Test
	void testLoadContents_whenTitleIsBlank_throwsException() {
		SiteContents contents = new SiteContents();
		contents.setTitle(" ");
		contents.setUrl("http://test.com");

		assertThrows(IllegalArgumentException.class, () -> parser.loadContents(contents));
	}

	@Test
	void testLoadContents_whenUrlIsBlank_throwsException() {
		SiteContents contents = new SiteContents();
		contents.setTitle("title");
		contents.setUrl("");

		assertThrows(IllegalArgumentException.class, () -> parser.loadContents(contents));
	}

	/**
	 * @see https://coveralls.io/builds/74902235/source?filename=src%2Fmain%2Fjava%2Fcom%2Fhappinesea%2Fwebcrawler%2FContentsParser.java#L31
	 * @throws Exception
	 */
	@Test
	void testLoadCategoryContentsList_whenNoElements_returnsNull() throws Exception {
		Document mockDoc = Jsoup.parse("<html><body></body></html>");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			List<SiteContents> result = parser.loadCategoryContentsList(mockCategory);
			assertThat(result).isNull();
		}
	}

	@Test
	void testLoadCategoryContentsList_whenSelectReturnsNull_returnsNull() throws Exception {
		Document mockDoc = mock(Document.class);
		when(mockDoc.select(mockCategory.getListRecordSelectId())).thenReturn(null);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			List<SiteContents> result = parser.loadCategoryContentsList(mockCategory);

			assertThat(result).isNull();
		}
	}

	@Test
	void testLoadCategoryContentsList_skipsBlankTitleAndFallsBackToHrefAttribute() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setCategoryUrl("http://test.com");
		category.setListRecordSelectId(".item");
		category.setTitleRecordSelectId(".title");
		category.setContentsUrlSelectId("a");
		Document mockDoc = Jsoup.parse("""
				<div class="item"><span class="title"></span><a href="/blank-title"></a></div>
				<div class="item"><span class="title">Valid title</span><a href="/valid-url"></a></div>
				<div class="item"><span class="title">No URL</span><span></span></div>
				""");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertEquals("Valid title", result.get(0).getTitle());
		assertEquals("http://test.com/valid-url", result.get(0).getUrl());
		}
	}

	@Test
	void testLoadCategoryContentsList_normalizesBroadContainerAndExtractsOneTitlePerArticle() throws Exception {
		SiteCategory category = new SiteCategory();
		category.setCategoryUrl("http://test.com");
		category.setListRecordSelectId("#uamods-topics");
		category.setTitleRecordSelectId("#uamods-topics");
		category.setContentsUrlSelectId("a[href*=/pickup/]");
		Document mockDoc = Jsoup.parse("""
				<div id="uamods-topics">
				  <ul>
				    <li data-ual-view-type="list">
				      <a href="https://news.yahoo.co.jp/pickup/1">
				        <div class="thumbnail"></div>
				        <div><div>First headline</div><div><time>6/20(土) 12:00</time></div></div>
				      </a>
				    </li>
				    <li data-ual-view-type="list">
				      <a href="https://news.yahoo.co.jp/pickup/2">
				        <div class="thumbnail"></div>
				        <div><div>Second headline</div><div><time>6/20(土) 11:00</time></div></div>
				      </a>
				    </li>
				  </ul>
				</div>
				""");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://test.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenReturn(mockDoc);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(2);
			assertEquals("First headline", result.get(0).getTitle());
			assertEquals("https://news.yahoo.co.jp/pickup/1", result.get(0).getUrl());
			assertEquals("Second headline", result.get(1).getTitle());
			assertEquals("https://news.yahoo.co.jp/pickup/2", result.get(1).getUrl());
		}
	}

	@Test
	void testLoadCategoryContentsList_retriesAfterFirstFetchFailure() throws Exception {
		ReflectionTestUtils.setField(parser, "externalConnectRetryCount", 2);
		Document mockDoc = Jsoup.parse("""
				<div class="item"><span class="title">Retry title</span><a href="/retry"></a></div>
				""");
		SiteCategory category = new SiteCategory();
		category.setCategoryUrl("http://retry.com");
		category.setListRecordSelectId(".item");
		category.setTitleRecordSelectId(".title");
		category.setContentsUrlSelectId("a");

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connectionMock = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect("http://retry.com")).thenReturn(connectionMock);
			mockConnectionChain(connectionMock);
			when(connectionMock.get()).thenThrow(new IOException("first failure")).thenReturn(mockDoc);

			List<SiteContents> result = parser.loadCategoryContentsList(category);

			assertThat(result).hasSize(1);
			assertEquals("Retry title", result.get(0).getTitle());
		}
	}

	/**
	 * @see https://coveralls.io/builds/74902235/source?filename=src%2Fmain%2Fjava%2Fcom%2Fhappinesea%2Fwebcrawler%2FContentsParser.java#L62
	 */
	@Test
	void testLoadContents_whenSiteCategoryIsNull_throwsException() {
		SiteContents contents = new SiteContents();
		contents.setTitle("title");
		contents.setUrl("http://test.com");

		IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
			parser.loadContents(contents);
		});

		assertThat(ex.getMessage()).contains("Invalid category info");
	}

	/**
	 * @see https://coveralls.io/builds/74902235/source?filename=src%2Fmain%2Fjava%2Fcom%2Fhappinesea%2Fwebcrawler%2FContentsParser.java#L70
	 * 
	 * @throws Exception
	 */
	@Test
	void testLoadContents_whenNoElementsFound_throwsNotFoundException() throws Exception {
	    // Arrange
	    SiteCategory category = new SiteCategory();
	    category.setBodySelectId("div.article"); // 鬩包ｽｩ陟冶侭竊醍ｹｧ・ｻ郢晢ｽｬ郢ｧ・ｯ郢ｧ・ｿ

	    SiteContents contents = new SiteContents();
	    contents.setUrl("http://test.com/article");
	    contents.setTitle("Some title");
	    contents.setSiteCategory(category);

	    // 驕ｨ・ｺ邵ｺ・ｮ Elements 郢ｧ螳夲ｽｿ譁絶・郢晢ｽ｢郢昴・縺・
	    Elements emptyElements = new Elements();

	    // Document 郢晢ｽ｢郢昴・縺・
	    Document mockDoc = mock(Document.class);
	    when(mockDoc.select("div.article")).thenReturn(emptyElements);  // 陟｢繝ｻ笘・→・ｺ郢ｧ螳夲ｽｿ譁絶・郢ｧ蛹ｻ竕ｧ邵ｺ・ｫ

	    // Jsoup 郢晢ｽ｢郢昴・縺・
	    try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
	        Connection connectionMock = mock(Connection.class);
	        jsoupMock.when(() -> Jsoup.connect("http://test.com/article")).thenReturn(connectionMock);
	        mockConnectionChain(connectionMock);
	        when(connectionMock.get()).thenReturn(mockDoc); // get() 遶翫・mockDoc 郢ｧ螳夲ｽｿ譁絶・

	        // Act & Assert
	        NotFoundContentsException ex = assertThrows(NotFoundContentsException.class, () -> {
	            parser.loadContents(contents);
	        });

	        // 隴帶ｺｷ・ｾ繝ｻ・・ｹｧ蠕鯉ｽ玖嵩蜿･・､謔ｶﾎ鍋ｹ昴・縺晉ｹ晢ｽｼ郢ｧ・ｸ繝ｻ繝ｻf 邵ｺ・ｮ闕ｳ・ｭ邵ｺ荵晢ｽ臥ｹｧ・ｹ郢晢ｽｭ郢晢ｽｼ邵ｺ霈費ｽ檎ｸｺ貅佩鍋ｹ昴・縺晉ｹ晢ｽｼ郢ｧ・ｸ繝ｻ繝ｻ
	        assertThat(ex.getMessage()).contains("Invalid load contents [http://test.com/article], null");
	    }
	}

	@Test
	void testLoadContents_usesFallbackBodySelectorWhenConfiguredSelectorIsBlank() throws Exception {
	    SiteCategory category = new SiteCategory();
	    category.setBodySelectId(" ");

	    SiteContents contents = new SiteContents();
	    contents.setUrl("http://test.com/fallback");
	    contents.setTitle("Some title");
	    contents.setSiteCategory(category);
	    Document mockDoc = Jsoup.parse("<article><p>Fallback body</p></article>");

	    try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
	        Connection connectionMock = mock(Connection.class);
	        jsoupMock.when(() -> Jsoup.connect("http://test.com/fallback")).thenReturn(connectionMock);
	        mockConnectionChain(connectionMock);
	        when(connectionMock.get()).thenReturn(mockDoc);

	        SiteContents result = parser.loadContents(contents);

	        assertThat(result.getContents()).contains("Fallback body");
	    }
	}

	private void mockConnectionChain(Connection connection) {
		when(connection.userAgent(anyString())).thenReturn(connection);
		when(connection.referrer(anyString())).thenReturn(connection);
		when(connection.timeout(anyInt())).thenReturn(connection);
	}

	private SiteContents loadNormalHtml(String html, String url, String bodySelector) throws Exception {
		SiteCategory category = new SiteCategory();
		category.setBodySelectId(bodySelector);
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContentsType(ContentsType.HTML);
		category.setSiteInfo(siteInfo);

		SiteContents contents = new SiteContents();
		contents.setUrl(url);
		contents.setTitle("Fixture title");
		contents.setSiteCategory(category);
		Document document = Jsoup.parse(html, url);

		try (MockedStatic<Jsoup> jsoupMock = mockStatic(Jsoup.class)) {
			Connection connection = mock(Connection.class);
			jsoupMock.when(() -> Jsoup.connect(url)).thenReturn(connection);
			mockConnectionChain(connection);
			when(connection.get()).thenReturn(document);
			return parser.loadContents(contents);
		}
	}

}
