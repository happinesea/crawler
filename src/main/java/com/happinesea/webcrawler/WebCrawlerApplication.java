package com.happinesea.webcrawler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class WebCrawlerApplication {

	public static void main(String[] args) {
		ConfigurableApplicationContext context = runApplication(args);
		System.exit(exitApplication(context));
	}

	// テスト用に runApplication メソッドを切り出し
	public static ConfigurableApplicationContext runApplication(String[] args) {
		return SpringApplication.run(WebCrawlerApplication.class, args);
	}

	static int exitApplication(ConfigurableApplicationContext context) {
		return SpringApplication.exit(context);
	}
}
