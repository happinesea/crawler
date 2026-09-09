package com.happinesea.webcrawler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.ExitCodeGenerator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class WebCrawlerApplicationExitTest {

	@Test
	void exitApplicationReturnsBatchExitCodeAndClosesContext() {
		AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		context.registerBean(ExitCodeGenerator.class, () -> () -> 7);
		context.refresh();

		assertEquals(7, WebCrawlerApplication.exitApplication(context));
		assertFalse(context.isActive());
	}
}
