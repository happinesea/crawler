package com.happinesea.crawler.entity;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SiteInfo {
    private @Id Integer siteInfoId;
    private String siteName;
    private String siteUrl;
    private String logoUrl;
    private String deleteFlg;
    private String contentsType;

}
