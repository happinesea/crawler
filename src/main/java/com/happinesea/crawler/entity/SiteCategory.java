package com.happinesea.crawler.entity;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SiteCategory {
    private @Id Integer siteCategoryId;
    private Integer siteInfoId;
    private String categoryName;
    private String categoryListUrl;
    private LocalDate lastScanTime;
    private String targetCategory;
    private String listRecordSelectId;
    private String titleRecordSelectId;
    private String contentsUrlSelectId;
    private String bodySelectId;
    private String moreBodySelectId;

}
