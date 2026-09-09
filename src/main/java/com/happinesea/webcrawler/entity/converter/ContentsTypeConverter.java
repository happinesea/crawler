package com.happinesea.webcrawler.entity.converter;

import com.happinesea.webcrawler.Const.ContentsType;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ContentsTypeConverter extends EnumAttributeConverter<ContentsType> {
    public ContentsTypeConverter() {
        super(ContentsType.class);
    }
}