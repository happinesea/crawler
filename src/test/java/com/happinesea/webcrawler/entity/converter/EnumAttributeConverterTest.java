package com.happinesea.webcrawler.entity.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.happinesea.webcrawler.Const.ProcessStatus;

class EnumAttributeConverterTest {

	@Test
	void testConvertToDatabaseColumn() {

		ProcessStatusConverter p = new ProcessStatusConverter();
		assertEquals("9", p.convertToDatabaseColumn(ProcessStatus.FAIL));
		assertNull(p.convertToDatabaseColumn(null));
	}

	@Test
	void testConvertToEntityAttribute() {
		ProcessStatusConverter p = new ProcessStatusConverter();
		assertNull(p.convertToEntityAttribute(null));
		assertEquals(ProcessStatus.FAIL, p.convertToEntityAttribute("9"));
		Exception exception = assertThrows(IllegalArgumentException.class, () -> {
			p.convertToEntityAttribute("x");
		});
		assertEquals("Unknown value: x for enum com.happinesea.webcrawler.Const$ProcessStatus", exception.getMessage());
	}

}
