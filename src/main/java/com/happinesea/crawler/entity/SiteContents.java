package com.happinesea.crawler.entity;

import org.springframework.data.annotation.Id;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SiteContents {
    private @Id Long siteContentsId;
    private String url;
    private String title;
    private String contents;
    private Long siteCategoyId;
    private String processStatus;

}
