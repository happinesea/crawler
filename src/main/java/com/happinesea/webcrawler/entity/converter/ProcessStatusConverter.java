package com.happinesea.webcrawler.entity.converter;

import com.happinesea.webcrawler.Const.ProcessStatus;

import jakarta.persistence.Converter;

@Converter(autoApply = false)
public class ProcessStatusConverter extends EnumAttributeConverter<ProcessStatus> {
    public ProcessStatusConverter() {
        super(ProcessStatus.class);
    }
}