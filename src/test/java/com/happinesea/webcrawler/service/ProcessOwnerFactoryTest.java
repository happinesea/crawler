package com.happinesea.webcrawler.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProcessOwnerFactoryTest {
	@Test
	void configuredInstanceIdBecomesOwnerInstancePrefix() {
		ProcessOwnerFactory factory = new ProcessOwnerFactory("gha-33848476897-1");

		assertEquals("gha-33848476897-1", factory.instanceId());
		assertTrue(factory.newOwner("job-675").startsWith("gha-33848476897-1/job-675/"));
	}

	@Test
	void blankConfiguredInstanceIdFallsBackToGeneratedInstance() {
		ProcessOwnerFactory factory = new ProcessOwnerFactory(" ");

		assertTrue(factory.instanceId().contains(":"));
		assertTrue(factory.newOwner("job-test").contains("/job-test/"));
	}
}
