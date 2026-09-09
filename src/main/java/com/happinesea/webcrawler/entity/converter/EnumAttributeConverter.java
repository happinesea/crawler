package com.happinesea.webcrawler.entity.converter;

import com.happinesea.webcrawler.PersistableEnum;

import jakarta.persistence.AttributeConverter;

public abstract class EnumAttributeConverter<T extends Enum<T> & PersistableEnum>
		implements AttributeConverter<T, String> {

	private final Class<T> enumClass;

	protected EnumAttributeConverter(Class<T> enumClass) {
		this.enumClass = enumClass;
	}

	@Override
	public String convertToDatabaseColumn(T attribute) {
		return attribute != null ? attribute.getValue() : null;
	}

	@Override
	public T convertToEntityAttribute(String dbData) {
		if (dbData == null)
			return null;

		for (T constant : enumClass.getEnumConstants()) {
			if (constant.getValue().equals(dbData)) {
				return constant;
			}
		}

		throw new IllegalArgumentException("Unknown value: " + dbData + " for enum " + enumClass.getName());
	}
}