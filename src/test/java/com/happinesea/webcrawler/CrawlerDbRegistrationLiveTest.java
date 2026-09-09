package com.happinesea.webcrawler;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.happinesea.webcrawler.Const.ProcessStatus;
import com.happinesea.webcrawler.config.CrawlerComponents;
import com.happinesea.webcrawler.entity.SiteCategory;
import com.happinesea.webcrawler.entity.SiteContents;
import com.happinesea.webcrawler.entity.SiteInfo;
import com.happinesea.webcrawler.entity.SiteInfoProcessPool;
import com.happinesea.webcrawler.repository.SiteCategoryRepository;
import com.happinesea.webcrawler.repository.SiteContentsRepository;
import com.happinesea.webcrawler.repository.SiteInfoProcessRepository;
import com.happinesea.webcrawler.repository.SiteInfoRepository;

@SpringBootTest(properties = {
		"web-crawler.skip-cms-post=true",
		"web-crawler.external-connect-timeout-ms=15000",
		"web-crawler.external-connect-retry-count=2"
})
@ActiveProfiles("test")
@AutoConfigureTestDatabase
@EnabledIfEnvironmentVariable(named = "CRAWLER_LIVE", matches = "true")
class CrawlerDbRegistrationLiveTest {
	private static final List<ProcessStatus> CRAWL_TARGET_STATUSES =
			List.of(ProcessStatus.NONE, ProcessStatus.FAIL, ProcessStatus.SUCCESS);


	@Autowired
	private CrawlerComponents crawlerComponents;
	@Autowired
	private SiteInfoRepository siteInfoRepository;
	@Autowired
	private SiteCategoryRepository siteCategoryRepository;
	@Autowired
	private SiteInfoProcessRepository siteInfoProcessRepository;
	@Autowired
	private SiteContentsRepository siteContentsRepository;

	private SiteInfoProcessPool processPool;

	@BeforeEach
	void setUp() {
		siteInfoProcessRepository.deleteAll();
		siteContentsRepository.deleteAll();
		siteCategoryRepository.deleteAll();
		siteInfoRepository.deleteAll();

		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setSiteName("Yahoo News");
		siteInfo.setSiteUrl("https://news.yahoo.co.jp");
		siteInfo = siteInfoRepository.save(siteInfo);

		SiteCategory category = new SiteCategory();
		category.setSiteInfo(siteInfo);
		category.setCategoryName("domestic");
		category.setCategoryUrl("https://news.yahoo.co.jp/topics/domestic");
		category.setListRecordSelectId("#uamods-topics > ul > li");
		category.setTitleRecordSelectId(
				"li[data-ual-view-type=\"list\"] a div:nth-of-type(2) div:not(:has(*)):not([class^=\"yads\"])");
		category.setContentsUrlSelectId("li[data-ual-view-type=\"list\"] > a");
		category.setBodySelectId("#uamods > div.article_body");
		category.setMoreBodySelectId("");
		category = siteCategoryRepository.save(category);

		processPool = new SiteInfoProcessPool();
		processPool.setSiteInfoProcessId(999001);
		processPool.setSiteCategory(category);
		processPool.setProcessStatus(ProcessStatus.NONE);
		processPool.setProcessTime(LocalDateTime.now());
		processPool = siteInfoProcessRepository.save(processPool);
	}

	@Test
	void crawlerShouldRegisterFetchedContentsIntoDatabase() throws Exception {
		List<SiteInfoProcessPool> targetProcesses =
				siteInfoProcessRepository.findByProcessStatusInOrderByProcessTimeAscSiteInfoProcessIdAsc(
						CRAWL_TARGET_STATUSES);
		ItemReader<SiteInfoProcessPool> reader = crawlerComponents.siteInfoProcessReader(targetProcesses);
		ItemProcessor<SiteInfoProcessPool, SiteInfoProcessPool> processor = crawlerComponents.siteInfoProcessor();
		ItemWriter<SiteInfoProcessPool> writer = crawlerComponents.siteInfoProcessWriter();

		SiteInfoProcessPool item = reader.read();
		assertThat(item).isNotNull();

		SiteInfoProcessPool processed = processor.process(item);
		writer.write(new Chunk<>(List.of(processed)));

		List<SiteContents> contents = siteContentsRepository.findAll();
		SiteInfoProcessPool savedProcess = siteInfoProcessRepository.findById(processPool.getSiteInfoProcessId())
				.orElseThrow();

		assertThat(contents).isNotEmpty();
		assertThat(contents).allSatisfy(content -> {
			assertThat(content.getUrl()).startsWith("https://news.yahoo.co.jp/");
			assertThat(content.getTitle()).isNotBlank();
			assertThat(content.getTitle()).hasSizeLessThanOrEqualTo(200);
			assertThat(content.getContents()).isNotBlank();
			assertThat(content.getDescription()).isNotBlank();
			assertThat(content.getDescription()).isNotEqualTo(content.getContents());
			assertThat(content.getProcessStatus()).isEqualTo(ProcessStatus.NONE);
		});
		assertThat(contents).extracting(SiteContents::getUrl).doesNotHaveDuplicates();
		assertThat(contents).anySatisfy(content -> assertThat(content.getContents())
				.contains("<img")
				.contains("src=\"https://"));
		assertThat(savedProcess.getProcessStatus()).isEqualTo(ProcessStatus.SUCCESS);
	}
}
