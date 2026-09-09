package com.happinesea.webcrawler.entity.converter;

import com.happinesea.webcrawler.Const.DeleteFlg;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class DeleteFlgConverter extends EnumAttributeConverter<DeleteFlg> {
    public DeleteFlgConverter() {
        super(DeleteFlg.class);
    }
}