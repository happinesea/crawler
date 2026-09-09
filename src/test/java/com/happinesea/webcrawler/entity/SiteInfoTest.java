package com.happinesea.webcrawler.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class SiteInfoTest {

	@ParameterizedTest
	@CsvSource({
			"0, false",
			"1, true",
			"2, true",
			"9, true",
			"unknown, false",
			"' ', false"
	})
	void exposesContentPublicationPolicyFromMasterContractType(String contractType, boolean expected) {
		SiteInfo siteInfo = new SiteInfo();
		siteInfo.setContractType(contractType);

		assertEquals(expected, siteInfo.allowsFullContent());
	}
}
